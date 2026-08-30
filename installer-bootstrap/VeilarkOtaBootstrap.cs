using System;
using System.Diagnostics;
using System.IO;
using System.Reflection;
using System.Security.Cryptography;
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

        string payloadPath = Path.Combine(
            directory,
            ".Veilark-payload-" + Process.GetCurrentProcess().Id + ".exe");

        try
        {
            PayloadMetadata expected = ReadPayloadMetadata();
            ExtractAndVerifyPayload(payloadPath, expected);

            if (args.Length == 1 &&
                String.Equals(args[0], "--bootstrap-verify-only", StringComparison.Ordinal))
            {
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
                    throw new InvalidOperationException("Не удалось запустить установщик Veilark.");
                }

                installer.WaitForExit();
                return installer.ExitCode;
            }
        }
        catch (Exception error)
        {
            WriteFailureLog(directory, error);
            return 1603;
        }
        finally
        {
            DeleteWithRetry(payloadPath);
        }
    }

    private static void ExtractAndVerifyPayload(string payloadPath, PayloadMetadata expected)
    {
        string temporaryPath = payloadPath + ".tmp";
        DeleteWithRetry(temporaryPath);

        long written = 0L;
        string copiedHash;
        Assembly assembly = Assembly.GetExecutingAssembly();
        using (Stream payload = assembly.GetManifestResourceStream(PayloadResource))
        {
            if (payload == null)
            {
                throw new InvalidDataException("В OTA-пакете отсутствует установщик Veilark.");
            }

            using (SHA256 sha256 = SHA256.Create())
            using (FileStream output = new FileStream(
                temporaryPath,
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
            DeleteWithRetry(temporaryPath);
            throw new InvalidDataException("Вложенный установщик Veilark повреждён.");
        }

        if (File.Exists(payloadPath))
        {
            File.Delete(payloadPath);
        }

        File.Move(temporaryPath, payloadPath);
        File.SetAttributes(payloadPath, FileAttributes.Hidden);
    }

    private static PayloadMetadata ReadPayloadMetadata()
    {
        string sizeValue = ReadTextResource(PayloadSizeResource);
        string sha256 = ReadTextResource(PayloadSha256Resource).ToUpperInvariant();
        long size;
        if (!Int64.TryParse(sizeValue, out size) || size <= 0L)
        {
            throw new InvalidDataException("В OTA-пакете указан неверный размер установщика.");
        }

        if (sha256.Length != 64 || !IsUpperHex(sha256))
        {
            throw new InvalidDataException("В OTA-пакете указана неверная контрольная сумма.");
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
                throw new InvalidDataException("В OTA-пакете отсутствуют метаданные установщика.");
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

    private static void DeleteWithRetry(string path)
    {
        for (int attempt = 0; attempt < 5; attempt++)
        {
            try
            {
                if (File.Exists(path))
                {
                    File.SetAttributes(path, FileAttributes.Normal);
                    File.Delete(path);
                }

                return;
            }
            catch
            {
                if (attempt == 4)
                {
                    return;
                }

                System.Threading.Thread.Sleep(200);
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
