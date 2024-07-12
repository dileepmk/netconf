package org.opendaylight.netconf.topology.singleton.impl.utils;

import net.schmizz.sshj.SSHClient;
import net.schmizz.sshj.sftp.RemoteResourceInfo;
import net.schmizz.sshj.sftp.SFTPClient;
import net.schmizz.sshj.transport.verification.HostKeyVerifier;
import net.schmizz.sshj.userauth.keyprovider.KeyProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.*;
import org.opendaylight.netconf.topology.singleton.messages.utils.SftpFileCollector;

import java.io.IOException;
import java.util.Arrays;

import static org.mockito.Mockito.*;

class SftpFileCollectorTest {

    @Mock
    private SSHClient sshClient;

    @Mock
    private KeyProvider keyProvider;

    @Mock
    private SFTPClient sftpClient;

    @Mock
    private RemoteResourceInfo remoteResourceInfo;

    @InjectMocks
    private SftpFileCollector sftpFileCollector;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        sftpFileCollector = new SftpFileCollector(sshClient); // Inject the mock SSHClient
    }

    @Test
    void testCollectFilesSuccess() throws Exception {
        // Arrange
        when(sshClient.loadKeys(anyString())).thenReturn(keyProvider);
        when(sshClient.newSFTPClient()).thenReturn(sftpClient);
        when(sftpClient.ls(anyString())).thenReturn(Arrays.asList(remoteResourceInfo));
        when(remoteResourceInfo.isDirectory()).thenReturn(false);
        when(remoteResourceInfo.getName()).thenReturn("testFile.txt");
        when(remoteResourceInfo.getPath()).thenReturn("/remote/path/testFile.txt");

        // Act
        sftpFileCollector.collect();

        // Assert
        verify(sshClient).addHostKeyVerifier(any(HostKeyVerifier.class));
        verify(sshClient).connect(anyString(), anyInt());
        verify(sshClient).loadKeys(anyString());
        verify(sshClient).authPublickey(anyString());
        verify(sshClient).setConnectTimeout(anyInt());
        verify(sshClient).setTimeout(anyInt());
        verify(sftpClient).ls(anyString());
        verify(sftpClient).get(anyString(), eq("testFile.txt"));
        verify(sftpClient).get(anyString(), eq("D:\\POC_Code\\SFTP_Retrieved_Files"));
        //verify(sshClient).disconnect();
    }

    @Test
    void testCollectFilesFailure() throws Exception {
        // Arrange
        when(sshClient.loadKeys(anyString())).thenReturn(keyProvider);
        when(sshClient.newSFTPClient()).thenReturn(sftpClient);
        when(sftpClient.ls(anyString())).thenThrow(new IOException("SFTP error"));

        // Act
        sftpFileCollector.collect();

        // Assert
        verify(sshClient).addHostKeyVerifier(any(HostKeyVerifier.class));
        verify(sshClient).connect(anyString(), anyInt());
        verify(sshClient).loadKeys(anyString());
        verify(sshClient).authPublickey(anyString());
        verify(sshClient).setConnectTimeout(anyInt());
        verify(sshClient).setTimeout(anyInt());
        verify(sftpClient).ls(anyString());
        //verify(sshClient).disconnect();
    }

    @Test
    void testDisconnectFailure() throws Exception {
        // Arrange
        when(sshClient.loadKeys(anyString())).thenReturn(keyProvider);
        when(sshClient.newSFTPClient()).thenReturn(sftpClient);
        when(sftpClient.ls(anyString())).thenReturn(Arrays.asList(remoteResourceInfo));
        when(sshClient.isConnected()).thenReturn(true);
        when(remoteResourceInfo.isDirectory()).thenReturn(false);
        when(remoteResourceInfo.getName()).thenReturn("testFile.txt");
        when(remoteResourceInfo.getPath()).thenReturn("/remote/path/testFile.txt");
        doThrow(new IOException("Disconnect error")).when(sshClient).disconnect();

        // Act
        sftpFileCollector.collect();

        // Assert
        verify(sshClient).addHostKeyVerifier(any(HostKeyVerifier.class));
        verify(sshClient).connect(anyString(), anyInt());
        verify(sshClient).loadKeys(anyString());
        verify(sshClient).authPublickey(anyString());
        verify(sshClient).setConnectTimeout(anyInt());
        verify(sshClient).setTimeout(anyInt());
        verify(sftpClient).ls(anyString());
        verify(sftpClient).get(anyString(), eq("testFile.txt"));
        verify(sftpClient).get(anyString(), eq("D:\\POC_Code\\SFTP_Retrieved_Files"));
        verify(sshClient).disconnect();
    }
}