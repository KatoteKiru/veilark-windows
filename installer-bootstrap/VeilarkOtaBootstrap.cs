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
[assembly: AssemblyVersion("0.3.6.0")]
[assembly: AssemblyFileVersion("0.3.6.0")]

internal static class VeilarkOtaBootstrap
{
    private const string PayloadResource = "Veilark.InstallerPayload";
    private const long ExpectedPayloadSize = 129672704L;
    private const string ExpectedPayloadSha256 =
        "5F29156C40BA192160BE4751B2BB73FFEBB6F55F846E5E076C3379DCA24A27F3";

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
            ".Veilark-0.3.6-payload-" + Process.GetCurrentProcess().Id + ".exe");

        try
        {
            ExtractAndVerifyPayload(payloadPath);

            if (args.Length == 1 &&
                String.Equals(args[0], "--bootstrap-verify-only", StringComparison.Ordinal))
            {
                Console.Out.WriteLine(ExpectedPayloadSha256);
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

    private static void ExtractAndVerifyPayload(string payloadPath)
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

        if (written != ExpectedPayloadSize ||
            !String.Equals(copiedHash, ExpectedPayloadSha256, StringComparison.Ordinal))
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
            string logPath = Path.Combine(directory, "bootstrap-306.log");
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
}
