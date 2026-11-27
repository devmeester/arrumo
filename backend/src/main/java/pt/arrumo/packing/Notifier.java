package pt.arrumo.packing;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.springframework.stereotype.Component;

/** Sends the JSON to a Power Automate flow ("When a Teams webhook request is received"). */
@Component
public class Notifier {

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    /** Returns null if it worked; otherwise the reason (in Portuguese, shown on the card). */
    public String send(String url, String json) {
        if (url == null || url.isBlank()) return "Envio não configurado: falta o URL do Power Automate (Embalamentos, Configurar envios).";
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url.strip()))
                    .timeout(Duration.ofSeconds(20))
                    .header("Content-Type", "application/json; charset=utf-8")
                    .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> r = http.send(request, HttpResponse.BodyHandlers.ofString());
            return r.statusCode() / 100 == 2 ? null : "O Power Automate respondeu com erro " + r.statusCode() + ".";
        } catch (IllegalArgumentException e) {
            return "O endereço do Power Automate não é válido.";
        } catch (IOException e) {
            return "Sem ligação ao Power Automate (o servidor tem acesso à internet?).";
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return "Envio interrompido.";
        }
    }
}
