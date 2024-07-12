package org.opendaylight.netconf.topology.singleton.messages.utils;

import net.schmizz.sshj.SSHClient;
import net.schmizz.sshj.sftp.RemoteResourceInfo;
import net.schmizz.sshj.sftp.SFTPClient;
import net.schmizz.sshj.userauth.keyprovider.KeyProvider;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

public class SftpFileCollector {

    private static final String SFTP_HOST = "AZN-BTMUM-PW00.wipro.com";
    private static final int SFTP_PORT = 4422;
    private static final String SFTP_USER = "6cc1537accfc80ce7e3ee61e71c68f418602e843e542b7f03cd99cc897b349fa";
    private static final String SFTP_REMOTE_DIRECTORY = "/home/NBiTS_UNX_ENDUSR_2022_2";
    private static final String PRIVATE_KEY_PATH = "C:\\Users\\DI20323195\\.ssh\\id_ed25519";
    private static final String LOCAL_DIRECTORY = "D:\\POC_Code\\SFTP_Retrieved_Files";

    private static final Logger logger = Logger.getLogger(SftpFileCollector.class.getName());

    private final SSHClient sshClient;

    public SftpFileCollector() {
        this(new SSHClient());
    }

    public SftpFileCollector(SSHClient sshClient) {
        this.sshClient = sshClient;
    }

    public static void main(String[] args) {
        ScheduledExecutorService executorService = Executors.newSingleThreadScheduledExecutor();
        executorService.scheduleAtFixedRate(SftpFileCollector::collectFiles, 0, 15, TimeUnit.MINUTES);
    }

    public static void collectFiles() {
        SftpFileCollector collector = new SftpFileCollector();
        try {
            collector.collect();
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Failed to collect files", e);
        }
    }

    public void collect() throws IOException {
        sshClient.addHostKeyVerifier((hostname, port, key) -> true); // Accept all host keys (use with caution)
        sshClient.connect(SFTP_HOST, SFTP_PORT);
        KeyProvider keyProvider = sshClient.loadKeys(PRIVATE_KEY_PATH);

        sshClient.authPublickey(SFTP_USER);
        sshClient.setConnectTimeout(10);
        sshClient.setTimeout(3);

        try (SFTPClient sftpClient = sshClient.newSFTPClient()) { // Use try-with-resources to ensure SFTPClient is closed
            List<RemoteResourceInfo> files = sftpClient.ls(SFTP_REMOTE_DIRECTORY);

            for (RemoteResourceInfo resource : files) {
                if (!resource.isDirectory()) {
                    String fileName = resource.getName();
                    sftpClient.get(resource.getPath(), fileName);
                    sftpClient.get(resource.getPath(), LOCAL_DIRECTORY);

                    logger.info("File collected: " + fileName); // Use logger instead of System.out
                }
            }
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error during SFTP operation", e); // Use logger instead of System.err
        } finally {
            try {
                if (sshClient.isConnected()) {
                    sshClient.disconnect();
                }
            } catch (IOException e) {
                logger.log(Level.SEVERE, "Error disconnecting", e); // Use logger instead of System.err
            }
        }
    }
}