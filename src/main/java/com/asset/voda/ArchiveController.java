package com.asset.voda;

import com.google.gson.JsonObject;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
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
            String boxRObjectId;
            try {
                String boxResponse = documentumService.callDocumentumAPI(httpClient, boxQuery);
                boxRObjectId = (String) documentumService.extractDocumentInfo(boxResponse, true);
                if (boxRObjectId == null) {
                    throw new Exception("No Box Number exists.");
                }
            } catch (Exception e) {
                response.put("status", "error");
                response.put("message", e.getMessage());
                return ResponseEntity.status(404).body(response.toMap());
            }

            // Step 2: Check if document exists
            String documentQuery = "select r_object_id, object_name, cch_mobile_no, cch_sim_no, cch_department_code, " +
                    "cch_comments, cch_sub_department_code, r_object_type " +
                    "from " + documentType + " where cch_customer_id = '" + customerId + "'";
            JsonObject documentDetails;
            try {
                String documentResponse = documentumService.callDocumentumAPI(httpClient, documentQuery);
                documentDetails = (JsonObject) documentumService.extractDocumentInfo(documentResponse, false);
                if (documentDetails == null) {
                    throw new Exception("No Document exists for the given Customer ID and Document Type.");
                }
            } catch (Exception e) {
                response.put("status", "error");
                response.put("message", e.getMessage());
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
            try {
                String createResponse = documentumService.createDocument(httpClient, boxRObjectId, metadata);
                if (createResponse == null) {
                    throw new Exception("Failed to create document.");
                }
            } catch (Exception e) {
                response.put("status", "error");
                response.put("message", e.getMessage());
                return ResponseEntity.status(500).body(response.toMap());
            }

            // Step 4: Update Document Metadata
            try {
                String updateResponse = documentumService.updateDocumentMetadata(httpClient, metadata.getR_object_id());
                return ResponseEntity.status(HttpStatus.OK)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(updateResponse);
            } catch (Exception e) {
                response.put("status", "error");
                response.put("message", e.getMessage());
                return ResponseEntity.status(500).body(response.toMap());
            }
        } catch (Exception e) {
            response.put("status", "error");
            response.put("message", e.getMessage());
            return ResponseEntity.status(500).body(response.toMap());
        }
    }
}
