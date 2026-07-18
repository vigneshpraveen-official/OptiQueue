package com.optiqueue.config;

import com.optiqueue.entity.Product;
import com.optiqueue.repository.ProductRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;

import java.math.BigDecimal;
import java.util.List;

/**
 * Optional demo catalog for the deployed showcase — enabled with
 * SEED_DEMO_PRODUCTS=true (optiqueue.seed.demo-products). Idempotent: only
 * seeds when the products table is empty.
 */
@Configuration
@ConditionalOnProperty(name = "optiqueue.seed.demo-products", havingValue = "true")
@Slf4j
public class DemoProductSeeder {

    private record Seed(String sku, String name, String price, int stock) {}

    private static final List<Seed> CATALOG = List.of(
            new Seed("PHONE-PIXA-128", "Pixa Smartphone 128GB", "34999.00", 42),
            new Seed("PHONE-NOVA-256", "Nova X Smartphone 256GB", "52999.00", 18),
            new Seed("LAPTOP-AIR-13", "AeroBook Air 13\"", "72999.00", 12),
            new Seed("LAPTOP-PRO-15", "AeroBook Pro 15\"", "119999.00", 7),
            new Seed("TAB-SLATE-11", "Slate Tab 11\"", "28999.00", 25),
            new Seed("WATCH-FIT-2", "FitPulse Watch 2", "8999.00", 60),
            new Seed("BUDS-AIR-P", "AirTune Buds Pro", "6499.00", 80),
            new Seed("HDPH-STUDIO", "Studio Over-Ear Headphones", "12999.00", 35),
            new Seed("CAM-ACT-4K", "TrailCam 4K Action Camera", "18499.00", 15),
            new Seed("TV-QLED-55", "Quantum QLED TV 55\"", "54999.00", 9),
            new Seed("SPKR-BOOM-X", "BoomBox X Speaker", "4999.00", 50),
            new Seed("KB-MECH-TKL", "Mech TKL Keyboard", "5499.00", 44),
            new Seed("MOUSE-PRO-W", "ProGlide Wireless Mouse", "2299.00", 90),
            new Seed("MON-IPS-27", "27\" IPS Monitor 144Hz", "21999.00", 20),
            new Seed("SSD-NVME-1T", "1TB NVMe SSD", "7499.00", 65),
            new Seed("RAM-DDR5-32", "32GB DDR5 Kit", "9999.00", 38),
            new Seed("ROUTER-AX6", "AX6000 WiFi Router", "11999.00", 22),
            new Seed("PWRBANK-20K", "20,000mAh Power Bank", "1999.00", 120),
            new Seed("CHRG-GAN-65", "65W GaN Charger", "2499.00", 75),
            new Seed("CABLE-C-2M", "USB-C Cable 2m (braided)", "499.00", 200),
            new Seed("BAG-LAP-15", "Laptop Backpack 15\"", "2999.00", 40),
            new Seed("DESK-STAND", "Aluminium Laptop Stand", "1799.00", 55),
            new Seed("HUB-USB-7", "7-in-1 USB-C Hub", "3499.00", 48),
            new Seed("LAMP-LED-D", "LED Desk Lamp", "1299.00", 66));

    @Bean
    @Order(2)   // after DemoUserBootstrap
    public CommandLineRunner seedDemoProducts(ProductRepository productRepository) {
        return args -> {
            if (productRepository.count() > 0) {
                return;
            }
            CATALOG.forEach(s -> productRepository.save(Product.builder()
                    .sku(s.sku())
                    .name(s.name())
                    .price(new BigDecimal(s.price()))
                    .stockQuantity(s.stock())
                    .build()));
            log.info("Seeded {} demo products", CATALOG.size());
        };
    }
}
