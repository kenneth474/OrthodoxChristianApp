package com.orthodox.orthodoxapp.account;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Tests the profile card, appearance settings and profile photos, through
 * the same web endpoints the account page uses.
 */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:profile;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
class ProfileTest {

    @Autowired
    private MockMvc mvc;

    @Test
    void savesPatronSaintWithTheirFeastDayAndHomeParish() throws Exception {
        MockHttpSession browser = register("profile@example.com");

        mvc.perform(put("/api/account/profile").session(browser).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"displayName": "Kenneth", "patronSaint": "St. Nicholas the Wonderworker",
                                 "nameDay": "12-06", "homeParish": "Saints Constantine and Helen, Carrollton"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.patronSaint").value("St. Nicholas the Wonderworker"))
                .andExpect(jsonPath("$.nameDay").value("12-06"))   // a month and day, no year
                .andExpect(jsonPath("$.homeParish").value("Saints Constantine and Helen, Carrollton"));
    }

    @Test
    void aFullDateFromAnOlderCopyOfTheAppKeepsJustTheMonthAndDay() throws Exception {
        MockHttpSession browser = register("olddate@example.com");
        mvc.perform(put("/api/account/profile").session(browser).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"displayName": "Test", "nameDay": "2026-12-06"}"""))
                .andExpect(jsonPath("$.nameDay").value("12-06"));
    }

    @Test
    void aFeastDayCanBeOnFebruary29th() throws Exception {
        // St. John Cassian: kept on February 29th in leap years.
        MockHttpSession browser = register("leap@example.com");
        mvc.perform(put("/api/account/profile").session(browser).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"displayName": "Test", "nameDay": "02-29"}"""))
                .andExpect(jsonPath("$.nameDay").value("02-29"));
    }

    @Test
    void anImpossibleFeastDayIsRefused() throws Exception {
        MockHttpSession browser = register("badday@example.com");
        mvc.perform(put("/api/account/profile").session(browser).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"displayName": "Test", "nameDay": "02-30"}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Choose a real month and day for the feast day."));
    }

    @Test
    void savesAPhoneNumberWithoutACode() throws Exception {
        MockHttpSession browser = register("phone@example.com");
        mvc.perform(put("/api/account/phone").session(browser).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"phone": "214.555.0177"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.phoneNumber").value("+12145550177"));
        // A blank number removes it.
        mvc.perform(put("/api/account/phone").session(browser).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"phone": ""}"""))
                .andExpect(jsonPath("$.phoneNumber").doesNotExist());
    }

    @Test
    void savesTextSizeThemeAndStyle() throws Exception {
        MockHttpSession browser = register("looks@example.com");
        mvc.perform(get("/api/account").session(browser))
                .andExpect(jsonPath("$.textSize").value("NORMAL"))
                .andExpect(jsonPath("$.theme").value("SYSTEM"))
                .andExpect(jsonPath("$.style").value("ILLUMINATED"));

        for (String size : new String[] {"SMALL", "LARGE", "NORMAL"}) {
            mvc.perform(put("/api/account/appearance").session(browser).with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"textSize": "%s", "theme": "DARK", "style": "VIGIL"}""".formatted(size)))
                    .andExpect(jsonPath("$.textSize").value(size))
                    .andExpect(jsonPath("$.theme").value("DARK"))
                    .andExpect(jsonPath("$.style").value("VIGIL"));
        }

        // Without a style (an older copy of the app), the chosen style stays.
        mvc.perform(put("/api/account/appearance").session(browser).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"textSize": "NORMAL", "theme": "LIGHT"}"""))
                .andExpect(jsonPath("$.style").value("VIGIL"));
    }

    @Test
    void theStyleCanBeChosenWhenCreatingTheAccount() throws Exception {
        MockHttpSession browser = new MockHttpSession();
        mvc.perform(post("/api/auth/register").session(browser).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "mosaic@example.com", "password": "Vespers-at-6:30pm",
                                 "displayName": "Test", "style": "MOSAIC"}"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.account.style").value("MOSAIC"));
    }

    @Test
    void uploadingAPhotoStoresASmallSquareJpeg() throws Exception {
        MockHttpSession browser = register("photo@example.com");
        mvc.perform(get("/api/account/photo").session(browser)).andExpect(status().isNotFound());

        // A wide 600 x 300 PNG, like a landscape photo.
        mvc.perform(multipart("/api/account/photo").file(photo(png(600, 300))).session(browser).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.photoVersion").isNumber());

        byte[] stored = mvc.perform(get("/api/account/photo").session(browser))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_JPEG))
                .andReturn().getResponse().getContentAsByteArray();
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(stored));
        assertNotNull(image, "the stored photo should be a readable image");
        assertEquals(256, image.getWidth());
        assertEquals(256, image.getHeight());

        mvc.perform(delete("/api/account/photo").session(browser).with(csrf()))
                .andExpect(jsonPath("$.photoVersion").doesNotExist());
        mvc.perform(get("/api/account/photo").session(browser)).andExpect(status().isNotFound());
    }

    @Test
    void somethingThatIsntAPhotoIsRefused() throws Exception {
        MockHttpSession browser = register("notaphoto@example.com");
        MockMultipartFile text = new MockMultipartFile("photo", "notes.png", "image/png", "hello".getBytes());

        mvc.perform(multipart("/api/account/photo").file(text).session(browser).with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Please choose a JPEG or PNG photo."));
    }

    @Test
    void photosArePrivate() throws Exception {
        mvc.perform(get("/api/account/photo")).andExpect(status().isUnauthorized());
    }

    // ----- Helpers -----

    private MockHttpSession register(String email) throws Exception {
        MockHttpSession browser = new MockHttpSession();
        mvc.perform(post("/api/auth/register").session(browser).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "%s", "password": "Vespers-at-6:30pm", "displayName": "Test"}"""
                                .formatted(email)))
                .andExpect(status().isCreated());
        return browser;
    }

    // Draws a simple picture in memory and saves it as PNG bytes.
    private static byte[] png(int width, int height) throws Exception {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.setColor(new Color(176, 138, 62)); // the app's gold
        g.fillOval(0, 0, width, height);
        g.dispose();
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        ImageIO.write(image, "png", bytes);
        return bytes.toByteArray();
    }

    private static MockMultipartFile photo(byte[] bytes) {
        return new MockMultipartFile("photo", "photo.png", "image/png", bytes);
    }
}
