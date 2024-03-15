package ru.lilaksy.learning;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

public class CrptApi {
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final ReentrantLock lock;
    private final Semaphore semaphore;

    public CrptApi(TimeUnit timeUnit, int requestLimit) {
        this.httpClient = HttpClient.newHttpClient();
        this.objectMapper = new ObjectMapper();
        this.lock = new ReentrantLock();
        this.semaphore = new Semaphore(requestLimit, true);
        //инициализуруем поля

        this.semaphore.acquireUninterruptibly(requestLimit);
        //захватываем и блокируем requestLimit на semaphore

        Thread thread = new Thread(() -> {
            try {
                TimeUnit.MILLISECONDS.sleep(timeUnit.toMillis(1));
                semaphore.release(requestLimit - semaphore.availablePermits());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        //создаем поток

        thread.setDaemon(true);
        //делаем поток демоном, чтобы данный поток не мешал закрыть программу в случае завершения работы остальных потоков

        thread.start();
        //запускаем поток
    }

    public void createDoc(Document document, String signature) throws IOException, InterruptedException {
        lock.lock();
        //блокируем, чтобы только один поток мог отправлять http запросы

        try {
            if(semaphore.tryAcquire()){     //попытка захватить разрешение из семафора
                Map<String, Object> requestBody = new HashMap<>();
                requestBody.put("document", document);
                requestBody.put("signature", signature);
                //создаем мапу тела запроса, добавляем туда документ и подпись

                String json = objectMapper.writeValueAsString(requestBody);
                //представляем документ и подпись в json

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create("https://ismp.crpt.ru/api/v3/lk/documents/create"))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(json))
                        .build();
                //создаем request

                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                //получаем response

                int statusCode = response.statusCode();
                if (statusCode == 200){
                    System.out.println("Request was successful");
                    System.out.println("Response body: " + response.body());
                }
                else{
                    System.out.println("Request failed with status code: " + statusCode);
                }
                //пример того, как мы можем обработать response
            }
        }
        finally {
            lock.unlock();
            //разблокируем
        }
    }

    public static class Document{
        //в данном классе можно определить поля документа
    }
}