package com.asset.voda;

import com.google.gson.JsonObject;
import com.google.gson.Gson;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

import org.json.JSONObject;

@RestController
@RequestMapping("/Vodafone/Archive")
public class ArchiveController {
    private final DocumentumService documentumService = new DocumentumService();

    @PostMapping
    public ResponseEntity<?> processRequest(@RequestBody DocumentRequest requestBody) {
        String customerId = requestBody.getCustomerId();
        String documentType = requestBody.getDocumentType();
        String boxNumber = requestBody.getBoxNumber();

        JSONObject response = new JSONObject();

        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            // Step 1: Check if box number exists
            String boxQuery = "select r_object_id from voda_box where object_name = 'BOX_" + boxNumber + "'";
            String boxResponse = documentumService.callDocumentumAPI(httpClient, boxQuery);
            String boxRObjectId = (String) documentumService.extractDocumentInfo(boxResponse, true);

            if (boxRObjectId == null) {
                response.put("status", "error");
                response.put("message", "No Box Number exists.");
                return ResponseEntity.status(404).body(response.toMap());
            }

            // Step 2: Check if document exists
            String documentQuery = "select r_object_id, object_name, cch_mobile_no, cch_sim_no, cch_department_code, " +
                    "cch_comments, cch_sub_department_code, r_object_type " +
                    "from " + documentType + " where cch_customer_id = '" + customerId + "'";
            String documentResponse = documentumService.callDocumentumAPI(httpClient, documentQuery);

            JsonObject documentDetails = (JsonObject) documentumService.extractDocumentInfo(documentResponse, false);
            if (documentDetails == null) {
                response.put("status", "error");
                response.put("message", "No Document exists for the given Customer ID.");
                return ResponseEntity.status(404).body(response.toMap());
            }

            // Extract fields from document details
            Metadata metadata = new Metadata();
            metadata.setR_object_id(documentDetails.get("r_object_id").getAsString());
            metadata.setObject_name(documentDetails.get("object_name").getAsString());
            metadata.setCch_mobile_no(documentDetails.get("cch_mobile_no").getAsString());
            metadata.setCch_sim_no(documentDetails.get("cch_sim_no").getAsString());
            metadata.setCch_department_code(documentDetails.get("cch_department_code").getAsString());
            metadata.setCch_comments(documentDetails.get("cch_comments").getAsString());
            metadata.setR_object_type(documentDetails.get("r_object_type").getAsString());

            // Step 3: Create Document
            String createResponse = documentumService.createDocument(httpClient, boxRObjectId, metadata);
            if (createResponse == null) {
                response.put("status", "error");
                response.put("message", "Failed to create document.");
                return ResponseEntity.status(500).body(response.toMap());
            }

            // Step 4: Update Document Metadata
            String updateResponse = documentumService.updateDocumentMetadata(httpClient, metadata.getR_object_id());

            // Convert String to Map for Jackson serialization using Gson
            Map<String, Object> updateResponseMap = new Gson().fromJson(updateResponse, Map.class);

            // Construct the final response
            Map<String, Object> finalResponse = new HashMap<>();
            finalResponse.put("status", "success");
            finalResponse.put("message", "Document processed successfully.");
            finalResponse.put("response", updateResponseMap); // Use map instead of String

            return ResponseEntity.ok(finalResponse);
        } catch (Exception e) {
            response.put("status", "error");
            response.put("message", e.getMessage());
            return ResponseEntity.status(500).body(response.toMap());
        }
    }
}
