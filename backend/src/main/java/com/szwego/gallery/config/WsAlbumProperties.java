package com.szwego.gallery.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "app.ws-album")
public class WsAlbumProperties {
    private String endpoint = "https://www.szwego.com/album/personal/all";
    private String tokenSecret = "change_this_ws_album_secret";
    private Integer crawlThreadPoolSize = 2;
}
