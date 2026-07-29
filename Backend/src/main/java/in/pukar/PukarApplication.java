package in.pukar;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class PukarApplication {
    public static void main(String[] args) {
        SpringApplication.run(PukarApplication.class, args);
    }
}
