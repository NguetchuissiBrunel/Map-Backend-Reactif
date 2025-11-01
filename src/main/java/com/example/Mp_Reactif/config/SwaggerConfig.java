package com.example.Mp_Reactif.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class SwaggerConfig {

    @Value("${RENDER_EXTERNAL_URL:http://localhost:8080}")
    private String renderUrl;

    @Bean
    public OpenAPI myOpenAPI() {
        Server server = new Server();
        server.setUrl(renderUrl);
        server.setDescription("https://map-backend-reactif.onrender.com");

        Contact contact = new Contact();
        contact.setName("Navigoo");
        contact.setEmail("Navigoo@team.com");
        contact.setUrl("https://example.com");

        License license = new License()
                .name("MIT License")
                .url("https://opensource.org/licenses/MIT");

        Info info = new Info()
                .title("API Lieux et Itinéraires - Cameroun")
                .version("1.0")
                .contact(contact)
                .license(license)
                .description("""
                    # 📍 API de Gestion des Lieux et Itinéraires - Cameroun
                    
                    ## 📖 Description
                    Cette API permet de rechercher des lieux géographiques au Cameroun et de calculer des itinéraires entre différents points.
                    
                    ## 🎯 Fonctionnalités principales
                    
                    ### 🔍 Recherche de lieux
                    - Recherche de lieux par nom dans la base de données locale
                    - Recherche automatique via OpenStreetMap si non trouvé en local
                    - Normalisation des noms (suppression des accents, minuscules)
                    - Validation géographique (uniquement dans les limites du Cameroun)
                    
                    ### 🗺️ Calcul d'itinéraires
                    - Itinéraires entre deux points avec différents modes de transport
                    - Itinéraires avec point de détour obligatoire
                    - Utilisation combinée de pgRouting (local) et OSRM (externe)
                    - Retourne jusqu'à 3 alternatives d'itinéraires
                    
                    ### 🚗 Modes de transport supportés
                    - **Voiture/Taxi** (`driving`)
                    - **Marche à pied** (`walking`) 
                    - **Vélo/Moto** (`cycling`)
                    - **Transport public** (`bus`, `taxi`)
                    
                    ## 🌍 Limites géographiques
                    Tous les points doivent être situés dans les limites du Cameroun :
                    - **Latitude** : 1.65° à 13.08°
                    - **Longitude** : 8.4° à 16.2°
                    
                    ## 🔐 Authentification
                    Cette API est actuellement publique et ne nécessite pas d'authentification.
                    
                    ## 📊 Codes de statut
                    - `200` : Succès
                    - `400` : Données invalides
                    - `404` : Ressource non trouvée
                    - `500` : Erreur serveur
                    
                    ## 🚀 Démarrage rapide
                    
                    ### 1. Rechercher un lieu
                    ```http
                    GET /api/places?name=Yaoundé
                    ```
                    
                    ### 2. Calculer un itinéraire
                    ```http
                    POST /api/routes
                    {
                      "points": [
                        {"lat": 3.8480, "lng": 11.5021},
                        {"lat": 4.0511, "lng": 9.7679}
                      ],
                      "mode": "driving"
                    }
                    ```
                    
                    ## 📞 Support
                    Pour toute question ou problème, contactez le support technique.
                    """);

        return new OpenAPI()
                .info(info)
                .servers(List.of(server));
    }
}