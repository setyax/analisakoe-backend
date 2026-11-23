package id.analisakoe;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@ConfigurationPropertiesScan
public class AnalisaKoeApplication {
    public static void main(String[] args) {
        SpringApplication.run(AnalisaKoeApplication.class, args);
    }
}