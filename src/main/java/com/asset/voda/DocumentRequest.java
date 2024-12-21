package com.asset.voda;

import lombok.Data;

@Data
public class DocumentRequest {
    private String customerId;
    private String documentType;
    private String boxNumber;
}
