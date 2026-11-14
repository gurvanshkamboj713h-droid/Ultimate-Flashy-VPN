package com.ultimateflashy.vpn;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

public final class VPNSettingsScreen extends Screen {
    private final Screen parent;
    private String protocol = "SOCKS5";
    private EditBox hostField;
    private EditBox portField;
    private EditBox usernameField;
    private EditBox passwordField;
    private String status = "Configure a proxy, then test it";
    private String latency = "";

    public VPNSettingsScreen(Screen parent) {
        super(Component.literal("Ultimate Flashy VPN"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int center = width / 2;
        int left = center - 100;

        addRenderableWidget(Button.builder(
            Component.literal("Protocol: " + protocol),
            button -> {
                protocol = protocol.equals("SOCKS5") ? "HTTP CONNECT" : "SOCKS5";
                button.setMessage(Component.literal("Protocol: " + protocol));
            }
        ).bounds(left, 43, 200, 20).build());

        hostField = new EditBox(font, left, 82, 200, 20, Component.literal("Proxy host"));
        hostField.setHint(Component.literal("proxy.example.com"));
        portField = new EditBox(font, left, 117, 200, 20, Component.literal("Port"));
        portField.setValue("1080");
        usernameField = new EditBox(font, left, 152, 200, 20, Component.literal("Username"));
        usernameField.setHint(Component.literal("Optional username"));
        passwordField = new EditBox(font, left, 187, 200, 20, Component.literal("Password"));
        passwordField.setHint(Component.literal("Optional password"));
        addRenderableWidget(hostField);
        addRenderableWidget(portField);
        addRenderableWidget(usernameField);
        addRenderableWidget(passwordField);

        addRenderableWidget(Button.builder(Component.literal("Save Profile"), button -> saveProfile())
            .bounds(left - 105, 225, 100, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Test Proxy"), button -> testProxy())
            .bounds(left + 5, 225, 100, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Back"), button -> Minecraft.getInstance().setScreen(parent))
            .bounds(center - 50, 265, 100, 20).build());

        loadProfile();
    }

    private Path configFile() {
        return Minecraft.getInstance().gameDirectory.toPath()
            .resolve("config")
            .resolve("ultimate-flashy-vpn.properties");
    }

    private void saveProfile() {
        try {
            Properties properties = new Properties();
            properties.setProperty("protocol", protocol);
            properties.setProperty("host", hostField.getValue());
            properties.setProperty("port", portField.getValue());
            properties.setProperty("username", usernameField.getValue());
            properties.setProperty("password", passwordField.getValue());
            Files.createDirectories(configFile().getParent());
            try (var output = Files.newOutputStream(configFile())) {
                properties.store(output, "Ultimate Flashy VPN profile");
            }
            status = "Profile saved locally";
        } catch (Exception exception) {
            status = "Could not save profile: " + safeMessage(exception);
        }
    }

    private void loadProfile() {
        if (!Files.exists(configFile())) return;
        try (var input = Files.newInputStream(configFile())) {
            Properties properties = new Properties();
            properties.load(input);
            protocol = properties.getProperty("protocol", "SOCKS5");
            hostField.setValue(properties.getProperty("host", ""));
            portField.setValue(properties.getProperty("port", "1080"));
            usernameField.setValue(properties.getProperty("username", ""));
            passwordField.setValue(properties.getProperty("password", ""));
            // Refresh the protocol button label after loading the saved setting.
            for (var child : children()) {
                if (child instanceof Button button && button.getMessage().getString().startsWith("Protocol:")) {
                    button.setMessage(Component.literal("Protocol: " + protocol));
                }
            }
        } catch (Exception exception) {
            status = "Could not load profile: " + safeMessage(exception);
        }
    }

    private void testProxy() {
        String host = hostField.getValue().trim();
        String portText = portField.getValue().trim();
        if (host.isEmpty()) {
            status = "Enter a proxy host first";
            latency = "";
            return;
        }
        final int port;
        try {
            port = Integer.parseInt(portText);
            if (port < 1 || port > 65535) throw new NumberFormatException();
        } catch (NumberFormatException exception) {
            status = "Enter a valid port (1–65535)";
            latency = "";
            return;
        }

        String selectedProtocol = protocol;
        String username = usernameField.getValue();
        String password = passwordField.getValue();
        status = "Testing proxy tunnel to example.com:443...";
        latency = "";

        Thread worker = new Thread(() -> {
            ProxyTester.Result result = ProxyTester.test(selectedProtocol, host, port, username, password);
            Minecraft.getInstance().execute(() -> {
                status = result.message();
                latency = result.success() ? result.millis() + " ms" : "";
            });
        }, "UltimateFlashyVPN-ProxyTest");
        worker.setDaemon(true);
        worker.start();
    }

    private static String safeMessage(Exception exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank() ? exception.getClass().getSimpleName() : message;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        graphics.fill(0, 0, width, height, 0xFF171321);
        graphics.fill(0, 0, width, 34, 0xFF302344);
        graphics.drawCenteredString(font, "✦ ULTIMATE FLASHY VPN", width / 2, 12, 0xFFE8D8FF);
        graphics.drawCenteredString(font, "Proxy settings", width / 2, 65, 0xFFCDB9E8);
        graphics.drawCenteredString(font, status, width / 2, 253, 0xFFFFFFFF);
        if (!latency.isEmpty()) {
            graphics.drawCenteredString(font, "Latency: " + latency, width / 2, 242, 0xFFBEEBC4);
        }
        super.render(graphics, mouseX, mouseY, delta);
    }
}
