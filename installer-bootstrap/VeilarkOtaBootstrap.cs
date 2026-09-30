using System;
using System.Diagnostics;
using System.IO;
using System.Reflection;
using System.Security.AccessControl;
using System.Security.Cryptography;
using System.Security.Principal;
using System.Text;

[assembly: AssemblyTitle("Veilark OTA Installer")]
[assembly: AssemblyDescription("Elevated in-place update bootstrap for Veilark")]
[assembly: AssemblyCompany("Veilark")]
[assembly: AssemblyProduct("Veilark")]
[assembly: AssemblyCopyright("Copyright (c) Veilark")]
internal static class VeilarkOtaBootstrap
{
    private const string PayloadResource = "Veilark.InstallerPayload";
    private const string PayloadSizeResource = "Veilark.PayloadSize";
    private const string PayloadSha256Resource = "Veilark.PayloadSha256";

    [STAThread]
    private static int Main(string[] args)
    {
        string bootstrapPath = Assembly.GetExecutingAssembly().Location;
        string directory = Path.GetDirectoryName(bootstrapPath);
        if (String.IsNullOrWhiteSpace(directory))
        {
            directory = Path.GetTempPath();
        }

        bool verifyOnly = args.Length == 1 &&
            String.Equals(args[0], "--bootstrap-verify-only", StringComparison.Ordinal);
        string workDirectory = null;
        FileStream payloadLock = null;
        try
        {
            PayloadMetadata expected = ReadPayloadMetadata();

            // The wrapper itself lives in the user-writable update directory.
            // An unelevated process of the same user must not be able to swap
            // the extracted payload between verification and the elevated
            // start, so the payload goes to a fresh directory that only
            // Administrators and SYSTEM can write, and a read-only share lock
            // is held from the final hash until the installer has exited.
            workDirectory = CreateWorkDirectory(verifyOnly);
            string payloadPath = Path.Combine(workDirectory, "Veilark-payload.exe");
            ExtractAndVerifyPayload(payloadPath, expected);
            payloadLock = OpenLockedAndVerify(payloadPath, expected);

            if (verifyOnly)
            {
                AssertWriteLocked(payloadPath);
                Console.Out.WriteLine(expected.Sha256);
                return 0;
            }

            ProcessStartInfo startInfo = new ProcessStartInfo();
            startInfo.FileName = payloadPath;
            startInfo.Arguments = BuildArguments(args);
            startInfo.UseShellExecute = false;
            startInfo.CreateNoWindow = true;

            using (Process installer = Process.Start(startInfo))
            {
                if (installer == null)
                {
                    throw new InvalidOperationException("Veilark installer could not be started.");
                }

                installer.WaitForExit();
                return installer.ExitCode;
            }
        }
        catch (Exception error)
        {
            if (verifyOnly)
            {
                Console.Error.WriteLine(error);
            }

            WriteFailureLog(directory, error);
            return 1603;
        }
        finally
        {
            if (payloadLock != null)
            {
                payloadLock.Dispose();
            }

            if (workDirectory != null)
            {
                DeleteDirectoryWithRetry(workDirectory);
            }
        }
    }

    private static bool IsElevated()
    {
        using (WindowsIdentity identity = WindowsIdentity.GetCurrent())
        {
            return new WindowsPrincipal(identity).IsInRole(WindowsBuiltInRole.Administrator);
        }
    }

    private static string CreateWorkDirectory(bool verifyOnly)
    {
        if (!IsElevated())
        {
            if (!verifyOnly)
            {
                throw new UnauthorizedAccessException("The Veilark OTA installer must run elevated.");
            }

            // A non-elevated verification never launches the payload.
            string userDirectory = Path.Combine(
                Path.GetTempPath(),
                "Veilark-ota-verify-" + Guid.NewGuid().ToString("N"));
            Directory.CreateDirectory(userDirectory);
            return userDirectory;
        }

        return CreateProtectedDirectory();
    }

    private static string CreateProtectedDirectory()
    {
        // %SystemRoot%\Temp lets users create entries but not delete or rename
        // entries owned by others, unlike %TEMP% or %LOCALAPPDATA%.
        string root = Path.Combine(
            Environment.GetFolderPath(Environment.SpecialFolder.Windows),
            "Temp");
        string path = Path.Combine(root, "Veilark-ota-" + Guid.NewGuid().ToString("N"));
        if (Directory.Exists(path) || File.Exists(path))
        {
            throw new IOException("The protected OTA directory already exists.");
        }

        Directory.CreateDirectory(path, ProtectedSecurity());
        DirectoryInfo created = new DirectoryInfo(path);
        if ((created.Attributes & FileAttributes.ReparsePoint) != 0)
        {
            throw new IOException("The protected OTA directory is a reparse point.");
        }

        AssertProtected(created.GetAccessControl(AccessControlSections.Access));
        return path;
    }

    private static DirectorySecurity ProtectedSecurity()
    {
        InheritanceFlags inherit = InheritanceFlags.ContainerInherit | InheritanceFlags.ObjectInherit;
        DirectorySecurity security = new DirectorySecurity();
        security.SetAccessRuleProtection(true, false);
        security.AddAccessRule(new FileSystemAccessRule(
            AdministratorsSid, FileSystemRights.FullControl, inherit, PropagationFlags.None, AccessControlType.Allow));
        security.AddAccessRule(new FileSystemAccessRule(
            SystemSid, FileSystemRights.FullControl, inherit, PropagationFlags.None, AccessControlType.Allow));
        // Replaces the owner's implicit WRITE_DAC: if the elevated token makes
        // the user (not Administrators) the owner, an unelevated process of
        // that user still cannot rewrite the DACL and gain write access.
        security.AddAccessRule(new FileSystemAccessRule(
            OwnerRightsSid, FileSystemRights.ReadAndExecute, inherit, PropagationFlags.None, AccessControlType.Allow));
        return security;
    }

    private static void AssertProtected(DirectorySecurity security)
    {
        if (!security.AreAccessRulesProtected)
        {
            throw new UnauthorizedAccessException("The OTA directory inherits permissions.");
        }

        const FileSystemRights writeRights =
            FileSystemRights.WriteData | FileSystemRights.AppendData | FileSystemRights.WriteExtendedAttributes |
            FileSystemRights.WriteAttributes | FileSystemRights.DeleteSubdirectoriesAndFiles |
            FileSystemRights.Delete | FileSystemRights.ChangePermissions | FileSystemRights.TakeOwnership;
        foreach (FileSystemAccessRule rule in security.GetAccessRules(true, true, typeof(SecurityIdentifier)))
        {
            if (rule.AccessControlType != AccessControlType.Allow)
            {
                continue;
            }

            SecurityIdentifier identity = (SecurityIdentifier)rule.IdentityReference;
            bool trusted = identity.Equals(AdministratorsSid) || identity.Equals(SystemSid);
            if (!trusted && (rule.FileSystemRights & writeRights) != 0)
            {
                throw new UnauthorizedAccessException("The OTA directory grants write access to " + identity.Value + ".");
            }
        }
    }

    private static readonly SecurityIdentifier AdministratorsSid =
        new SecurityIdentifier(WellKnownSidType.BuiltinAdministratorsSid, null);
    private static readonly SecurityIdentifier SystemSid =
        new SecurityIdentifier(WellKnownSidType.LocalSystemSid, null);
    private static readonly SecurityIdentifier OwnerRightsSid = new SecurityIdentifier("S-1-3-4");

    private static void ExtractAndVerifyPayload(string payloadPath, PayloadMetadata expected)
    {
        long written = 0L;
        string copiedHash;
        Assembly assembly = Assembly.GetExecutingAssembly();
        using (Stream payload = assembly.GetManifestResourceStream(PayloadResource))
        {
            if (payload == null)
            {
                throw new InvalidDataException("The OTA package does not contain the Veilark installer.");
            }

            using (SHA256 sha256 = SHA256.Create())
            using (FileStream output = new FileStream(
                payloadPath,
                FileMode.CreateNew,
                FileAccess.Write,
                FileShare.None))
            {
                byte[] buffer = new byte[1024 * 1024];
                int count;
                while ((count = payload.Read(buffer, 0, buffer.Length)) > 0)
                {
                    output.Write(buffer, 0, count);
                    sha256.TransformBlock(buffer, 0, count, null, 0);
                    written += count;
                }

                sha256.TransformFinalBlock(new byte[0], 0, 0);
                output.Flush(true);
                copiedHash = ToHex(sha256.Hash);
            }
        }

        if (written != expected.Size ||
            !String.Equals(copiedHash, expected.Sha256, StringComparison.Ordinal))
        {
            throw new InvalidDataException("The embedded Veilark installer is corrupted.");
        }
    }

    /// Opens the extracted payload with read-only sharing, which denies every
    /// later writer and deleter (including rename), and hashes the exact bytes
    /// that CreateProcess will map. The caller keeps the handle until exit.
    private static FileStream OpenLockedAndVerify(string payloadPath, PayloadMetadata expected)
    {
        FileStream locked = new FileStream(payloadPath, FileMode.Open, FileAccess.Read, FileShare.Read);
        try
        {
            if (locked.Length != expected.Size)
            {
                throw new InvalidDataException("The extracted Veilark installer changed size.");
            }

            string actual;
            using (SHA256 sha256 = SHA256.Create())
            {
                actual = ToHex(sha256.ComputeHash(locked));
            }

            if (!String.Equals(actual, expected.Sha256, StringComparison.Ordinal))
            {
                throw new InvalidDataException("The extracted Veilark installer changed before launch.");
            }

            locked.Position = 0L;
            return locked;
        }
        catch
        {
            locked.Dispose();
            throw;
        }
    }

    private static void AssertWriteLocked(string payloadPath)
    {
        try
        {
            using (new FileStream(payloadPath, FileMode.Open, FileAccess.ReadWrite, FileShare.ReadWrite | FileShare.Delete))
            {
            }
        }
        catch (IOException)
        {
            return;
        }

        throw new InvalidOperationException("The verified payload is not protected against replacement.");
    }

    private static PayloadMetadata ReadPayloadMetadata()
    {
        string sizeValue = ReadTextResource(PayloadSizeResource);
        string sha256 = ReadTextResource(PayloadSha256Resource).ToUpperInvariant();
        long size;
        if (!Int64.TryParse(sizeValue, out size) || size <= 0L)
        {
            throw new InvalidDataException("The OTA package declares an invalid installer size.");
        }

        if (sha256.Length != 64 || !IsUpperHex(sha256))
        {
            throw new InvalidDataException("The OTA package declares an invalid checksum.");
        }

        return new PayloadMetadata(size, sha256);
    }

    private static string ReadTextResource(string name)
    {
        Assembly assembly = Assembly.GetExecutingAssembly();
        using (Stream stream = assembly.GetManifestResourceStream(name))
        {
            if (stream == null)
            {
                throw new InvalidDataException("The OTA package is missing installer metadata.");
            }

            using (StreamReader reader = new StreamReader(stream, Encoding.ASCII, false))
            {
                return reader.ReadToEnd().Trim();
            }
        }
    }

    private static bool IsUpperHex(string value)
    {
        foreach (char character in value)
        {
            if (!((character >= '0' && character <= '9') ||
                  (character >= 'A' && character <= 'F')))
            {
                return false;
            }
        }

        return true;
    }

    private static string BuildArguments(string[] args)
    {
        StringBuilder commandLine = new StringBuilder();
        for (int index = 0; index < args.Length; index++)
        {
            if (index > 0)
            {
                commandLine.Append(' ');
            }

            commandLine.Append(QuoteArgument(args[index]));
        }

        return commandLine.ToString();
    }

    private static string QuoteArgument(string argument)
    {
        if (argument.Length > 0 &&
            argument.IndexOfAny(new[] { ' ', '\t', '\n', '\v', '"' }) < 0)
        {
            return argument;
        }

        StringBuilder quoted = new StringBuilder();
        quoted.Append('"');
        int backslashes = 0;
        foreach (char character in argument)
        {
            if (character == '\\')
            {
                backslashes++;
                continue;
            }

            if (character == '"')
            {
                quoted.Append('\\', backslashes * 2 + 1);
                quoted.Append('"');
                backslashes = 0;
                continue;
            }

            quoted.Append('\\', backslashes);
            backslashes = 0;
            quoted.Append(character);
        }

        quoted.Append('\\', backslashes * 2);
        quoted.Append('"');
        return quoted.ToString();
    }

    private static string ToHex(byte[] bytes)
    {
        StringBuilder result = new StringBuilder(bytes.Length * 2);
        foreach (byte value in bytes)
        {
            result.Append(value.ToString("X2"));
        }

        return result.ToString();
    }

    private static void WriteFailureLog(string directory, Exception error)
    {
        try
        {
            string logPath = Path.Combine(directory, "bootstrap.log");
            // Never follow a link planted in the user-writable directory.
            if ((File.GetAttributes(directory) & FileAttributes.ReparsePoint) != 0 ||
                (File.Exists(logPath) && (File.GetAttributes(logPath) & FileAttributes.ReparsePoint) != 0))
            {
                return;
            }

            File.AppendAllText(
                logPath,
                DateTime.UtcNow.ToString("O") + " " + error + Environment.NewLine,
                Encoding.UTF8);
        }
        catch
        {
            // The updater will still receive exit code 1603.
        }
    }

    private static void DeleteDirectoryWithRetry(string path)
    {
        for (int attempt = 0; attempt < 10; attempt++)
        {
            try
            {
                if (!Directory.Exists(path))
                {
                    return;
                }

                foreach (string file in Directory.GetFiles(path))
                {
                    File.SetAttributes(file, FileAttributes.Normal);
                }

                Directory.Delete(path, true);
                return;
            }
            catch
            {
                System.Threading.Thread.Sleep(300);
            }
        }
    }

    private sealed class PayloadMetadata
    {
        internal PayloadMetadata(long size, string sha256)
        {
            Size = size;
            Sha256 = sha256;
        }

        internal long Size { get; private set; }
        internal string Sha256 { get; private set; }
    }
}
