package org.example;

import java.io.IOException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;


@Slf4j
public class CrptApi {
    private static final String API_URL = "https://ismp.crpt.ru/api/v3/lk/documents/create";
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    private final OkHttpClient client;
    private final ObjectMapper objectMapper;
    private final Semaphore semaphore;
    private final ScheduledExecutorService scheduler;

    public CrptApi(TimeUnit timeUnit, int requestLimit) {
        this.client = new OkHttpClient();
        this.objectMapper = new ObjectMapper();
        this.semaphore = new Semaphore(requestLimit);
        this.scheduler = Executors.newScheduledThreadPool(1);

        scheduler.scheduleAtFixedRate(() -> semaphore.release(requestLimit - semaphore.availablePermits()), 0, 1, timeUnit);
    }

    public void createDocument(Document document, String signature) throws InterruptedException, CrptApiException {
        semaphore.acquire();
        try {
            String json = objectMapper.writeValueAsString(document);
            RequestBody body = RequestBody.create(json, JSON);
            Request request = new Request.Builder()
                    .url(API_URL)
                    .post(body)
                    .addHeader("Signature", signature)
                    .build();

            try (Response response = client.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    throw new CrptApiException("Unexpected response code: " + response);
                }
                log.info(response.body().string());
            }


        } catch (IOException e) {
            throw new CrptApiException("IO Exception during API call", e);
        }
    }

    @Data
    @AllArgsConstructor
    public static class Document {
        private String description;
        private String docId;
        private String docStatus;
        private String docType;
        private boolean importRequest;
        private String ownerInn;
        private String participantInn;
        private String producerInn;
        private String productionDate;
        private String productionType;
        private Product[] products;
        private String regDate;
        private String regNumber;

        @Data
        @AllArgsConstructor
        public static class Product {
            private String certificateDocument;
            private String certificateDocumentDate;
            private String certificateDocumentNumber;
            private String ownerInn;
            private String producerInn;
            private String productionDate;
            private String tnvedCode;
            private String uitCode;
            private String uituCode;
        }
    }

    public static class CrptApiException extends Exception {
        public CrptApiException(String message) {
            super(message);
            CrptApi.log.error(message);
        }

        public CrptApiException(String message, Throwable cause) {
            super(message, cause);
            CrptApi.log.error(message, cause);
        }
    }

    public static void main(String[] args) {
        CrptApi api = new CrptApi(TimeUnit.SECONDS, 2);

        Document.Product product = new Document.Product(
                "cert_doc",
                "2020-01-23",
                "cert_num",
                "owner_inn",
                "producer_inn",
                "2020-01-23",
                "tnved_code",
                "uit_code",
                "uitu_code"
        );

        Document document = new Document(
                "{\"participantInn\": \"1234567890\"}",
                "doc_id",
                "doc_status",
                "LP_INTRODUCE_GOODS",
                true,
                "owner_inn",
                "participant_inn",
                "producer_inn",
                "2020-01-23",
                "production_type",
                new Document.Product[] { product },
                "2020-01-23",
                "reg_number"
        );

        try {
            api.createDocument(document, "signature");
            api.createDocument(document, "signature");
            api.createDocument(document, "signature");
            api.createDocument(document, "signature");
            api.createDocument(document, "signature");
            api.createDocument(document, "signature");
        } catch (InterruptedException | CrptApiException e) {
            CrptApi.log.error("Error creating document", e);
        }
    }
}
