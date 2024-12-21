package com.asset.voda;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.core5.http.ParseException;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.entity.StringEntity;

import java.io.IOException;
import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

public class DocumentumService {

    // Define static fields for configuration
    private static final String DOC_API_URL;
    private static final String DOC_API_AUTH_HEADER;

    // Static block to load properties
    static {
        Properties properties = new Properties();
        try {
            // Load the config.properties file from the classpath
            InputStream inputStream = DocumentumService.class.getClassLoader().getResourceAsStream("config.properties");

            properties.load(inputStream);
            DOC_API_URL = properties.getProperty("DOC_API_URL");
            DOC_API_AUTH_HEADER = properties.getProperty("DOC_API_AUTH_HEADER");

        } catch (IOException e) {
            throw new RuntimeException("Could not load configuration from config.properties", e);
        }
    }

    private HttpPost prepareRequest(String url, String jsonBody) {
        HttpPost request = new HttpPost(url);
        request.setHeader("Authorization", DOC_API_AUTH_HEADER);
        request.setHeader("Content-Type", "application/json");

        if (jsonBody != null) {
            request.setEntity(new StringEntity(jsonBody, StandardCharsets.UTF_8));
        }
        return request;
    }

    public String callDocumentumAPI(CloseableHttpClient httpClient, String dqlQuery) throws IOException {
        String encodedQuery = URLEncoder.encode(dqlQuery, StandardCharsets.UTF_8);
        HttpPost request = prepareRequest(DOC_API_URL + "?dql=" + encodedQuery, null);

        try (CloseableHttpResponse response = httpClient.execute(request)) {
            if (response.getCode() != 200) {
                throw new IOException("Error executing DQL query. Status: " + response.getCode());
            }
            return EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);
        } catch (ParseException e) {
            throw new RuntimeException(e);
        }
    }

    // Generic method to extract either r_object_id or document details
    public Object extractDocumentInfo(String response, boolean extractRObjectId) {
        JsonObject jsonResponse = JsonParser.parseString(response).getAsJsonObject();
        JsonArray entries = jsonResponse.getAsJsonArray("entries");

        if (entries != null && entries.size() > 0) {
            JsonObject entry = entries.get(0).getAsJsonObject();
            JsonObject content = entry.getAsJsonObject("content");
            JsonObject properties = content.getAsJsonObject("properties");

            if (extractRObjectId) {
                return properties.get("r_object_id").getAsString(); // Return only r_object_id
            } else {
                return properties; // Return full document properties
            }
        }

        return null; // If no entries found, return null
    }

    public String createDocument(CloseableHttpClient httpClient, String folderRObjectId, Metadata metadata) throws IOException {
        String createUrl = DOC_API_URL + "/folders/" + folderRObjectId + "/documents";

        JsonObject metadataJson = new JsonObject();
        JsonObject properties = new JsonObject();

        properties.addProperty("object_name", "Doc_" + metadata.getObject_name());
        properties.addProperty("r_object_type", "voda_based_on_document");
        properties.addProperty("cch_mobile_no", metadata.getCch_mobile_no());
        properties.addProperty("cch_sim_no", metadata.getCch_sim_no());
        properties.addProperty("cch_department_code", metadata.getCch_department_code());
        properties.addProperty("cch_comments", metadata.getCch_comments());
        properties.addProperty("cch_doc_type_code", metadata.getR_object_type());

        metadataJson.add("properties", properties);

        // Prepare and execute the HTTP request to create the document
        HttpPost request = prepareRequest(createUrl, metadataJson.toString());

        try (CloseableHttpResponse response = httpClient.execute(request)) {
            if (response.getCode() != 200 && response.getCode() != 201) {
                throw new IOException("Failed to create document. Status: " + response.getCode());
            }
            return EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);
        } catch (ParseException e) {
            throw new RuntimeException(e);
        }
    }

    public String updateDocumentMetadata(CloseableHttpClient httpClient, String rObjectId) throws IOException, ParseException {
        String updateUrl = DOC_API_URL + "/objects/" + rObjectId;
        String jsonBody = "{ \"properties\": { \"cch_status\": \"ARCHIVED\" } }";

        HttpPost request = prepareRequest(updateUrl, jsonBody);

        try (CloseableHttpResponse response = httpClient.execute(request)) {
            if (response.getCode() != 200) {
                throw new IOException("Failed to update document metadata. Status: " + response.getCode());
            }
            return EntityUtils.toString(response.getEntity());
        } catch (IOException e) {
            throw new IOException("Error while updating document metadata", e);
        }
    }
}
