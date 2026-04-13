package com.cascadeAI.Orchestrator.service;

import com.cascadeAI.Orchestrator.config.GDriveProperties;
import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp;
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.InputStreamContent;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.util.store.FileDataStoreFactory;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.DriveScopes;
import com.google.api.services.drive.model.File;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStreamReader;
import java.security.GeneralSecurityException;
import java.util.Collections;
import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class GoogleDriveService {

    private static final GsonFactory JSON_FACTORY = GsonFactory.getDefaultInstance();
    private static final List<String> SCOPES = Collections.singletonList(DriveScopes.DRIVE_FILE);
    private static final String TOKENS_DIR = "tokens";
    private final ParameterStoreService parameterStoreService;
    private final GDriveProperties gDriveProperties;
    private final ResourceLoader resourceLoader;

    private Drive driveService;

    @PostConstruct
    public void init() throws GeneralSecurityException, IOException {
        NetHttpTransport httpTransport = GoogleNetHttpTransport.newTrustedTransport();
        Credential credential = authorize(httpTransport);

        driveService = new Drive.Builder(httpTransport, JSON_FACTORY, credential)
                .setApplicationName("CascadeAI-Orchestrator")
                .build();

        log.info("Google Drive service initialized, target folder: {}", gDriveProperties.getFolderId());
    }

    private Credential authorize(NetHttpTransport httpTransport) throws IOException {
        var in = resourceLoader.getResource(gDriveProperties.getCredentialsPath()).getInputStream();
        GoogleClientSecrets clientSecrets = GoogleClientSecrets.load(JSON_FACTORY, new InputStreamReader(in));

        GoogleAuthorizationCodeFlow flow = new GoogleAuthorizationCodeFlow.Builder(
                httpTransport, JSON_FACTORY, clientSecrets, SCOPES)
                .setDataStoreFactory(new FileDataStoreFactory(new java.io.File(TOKENS_DIR)))
                .setAccessType("offline")
                .build();

        LocalServerReceiver receiver = new LocalServerReceiver.Builder().setPort(8888).build();
        return new AuthorizationCodeInstalledApp(flow, receiver).authorize("user");
    }

    public File uploadFile(MultipartFile multipartFile) throws IOException {

        String gDriveFolderId = parameterStoreService.getParameterValue(gDriveProperties.getFolderId());

        File fileMetadata = new File();
        fileMetadata.setName(multipartFile.getOriginalFilename());
        fileMetadata.setParents(List.of(gDriveFolderId));

        InputStreamContent mediaContent = new InputStreamContent(
                multipartFile.getContentType(),
                multipartFile.getInputStream());
        mediaContent.setLength(multipartFile.getSize());

        File uploaded = driveService.files().create(fileMetadata, mediaContent)
                .setFields("id, name, webViewLink")
                .execute();

        log.info("Uploaded '{}' to Google Drive, fileId={}", uploaded.getName(), uploaded.getId());
        return uploaded;
    }
}
