package io.github.eylexlive.discord2fa.util;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.time.OffsetDateTime;

import com.google.gson.*;

public class DiscordWebhook {

    private static final Gson gson = new GsonBuilder().create();
    
    public static void sendWebhook(String webhookUrl, String embedTitle, String embedContent, String embedImage, String fieldName, String fieldValue, String fieldName2, String fieldValue2) {

        try {

            URL url = new URL(webhookUrl);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setDoOutput(true);

            // Crear el objeto JSON para el mensaje embed
            JsonObject embedJson = new JsonObject();
            embedJson.addProperty("title", embedTitle);
            embedJson.addProperty("description", embedContent);

            // Agregar la imagen al embed
            JsonObject imageJson = new JsonObject();
            imageJson.addProperty("url", embedImage);
            embedJson.add("thumbnail", imageJson);

            // Agregar campos adicionales al embed
            JsonArray fieldsArray = new JsonArray();

            JsonObject field1Json = new JsonObject();
            field1Json.addProperty("name", fieldName);
            field1Json.addProperty("value", fieldValue);
            field1Json.addProperty("inline", true);
            fieldsArray.add(field1Json);

            JsonObject field2Json = new JsonObject();
            field2Json.addProperty("name", fieldName2);
            field2Json.addProperty("value", fieldValue2);
            field2Json.addProperty("inline", true);
            fieldsArray.add(field2Json);

            embedJson.add("fields", fieldsArray);

            // Crear el objeto JSON para el webhook
            JsonObject webhookJson = new JsonObject();
            webhookJson.addProperty("content", "");
            webhookJson.add("embeds", gson.toJsonTree(new JsonObject[]{embedJson}));

            // Seteamos el footer con la fecha y hora actual
            JsonObject footerJson = new JsonObject();
            footerJson.addProperty("text", OffsetDateTime.now().toString());

            String json = gson.toJson(webhookJson);

            OutputStream outputStream = connection.getOutputStream();
            outputStream.write(json.getBytes("UTF-8"));
            outputStream.close();

            int responseCode = connection.getResponseCode();

            if (responseCode != 204) {
                System.out.println("Error al enviar el mensaje embed a Discord. Codigo de respuesta: " + responseCode);
            } else {
                System.out.println("Mensaje embed enviado a Discord.");
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

    }


}
