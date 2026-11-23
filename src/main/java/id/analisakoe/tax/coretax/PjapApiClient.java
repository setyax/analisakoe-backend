package id.analisakoe.tax.coretax;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.boot.web.client.RestTemplateBuilder;

import java.util.Base64;

@Service
@org.springframework.context.annotation.Lazy
public class PjapApiClient {
    private final RestTemplate restTemplate;
    private final String baseUrl;
    private final String username;
    private final String password;
    private final String companyNpwp;

    public PjapApiClient(RestTemplateBuilder builder,
                         @Value("${app.coretax.base-url:}") String baseUrl,
                         @Value("${app.coretax.username:}") String username,
                         @Value("${app.coretax.password:}") String password,
                         @Value("${app.coretax.company-npwp:}") String companyNpwp) {
        this.restTemplate = builder.build();
        this.baseUrl = baseUrl; this.username = username; this.password = password; this.companyNpwp = companyNpwp;
    }

    public String submitEFaktur(String xmlPayload) {
        // HTTP Basic Auth
        String auth = username + ":" + password;
        byte[] encodedAuth = Base64.getEncoder().encode(auth.getBytes());
        String authHeader = "Basic " + new String(encodedAuth);

        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", authHeader);
        headers.set("npwp", companyNpwp);
        headers.setContentType(MediaType.APPLICATION_XML);

        HttpEntity<String> request = new HttpEntity<>(xmlPayload, headers);
        
        // Endpoint contoh. Sesuaikan dengan dokumentasi PJAP Anda.
        ResponseEntity<String> response = restTemplate.postForEntity(baseUrl + "/v1/efaktur/upload", request, String.class);
        return response.getBody();
    }
}