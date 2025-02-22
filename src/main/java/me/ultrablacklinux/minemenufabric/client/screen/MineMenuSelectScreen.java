package me.ultrablacklinux.minemenufabric.client.screen;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.systems.RenderSystem;
import me.ultrablacklinux.minemenufabric.client.MineMenuFabricClient;
import me.ultrablacklinux.minemenufabric.client.config.Config;
import me.ultrablacklinux.minemenufabric.client.util.AngleHelper;
import me.ultrablacklinux.minemenufabric.client.util.GsonUtil;
import me.ultrablacklinux.minemenufabric.client.util.RandomUtil;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.options.KeyBinding;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.InputUtil;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableText;
import net.minecraft.util.Util;
import org.lwjgl.opengl.GL11;

import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.Map;

import static me.ultrablacklinux.minemenufabric.client.MineMenuFabricClient.*;


/**
 * The original menu was by FlashyReese
 */

@SuppressWarnings("ConstantConditions")
public class MineMenuSelectScreen extends Screen {
    private final JsonObject jsonItems; //MUST NEVER BE STATIC - WILL BE NULL OTHERWISE
    private final int circleEntries;
    private final int outerRadius;
    private final int innerRadius;

    private ButtonWidget repeatButton;

    public MineMenuSelectScreen(JsonObject menuData, String menuTitle, Screen parent) {
        super(Text.of(menuTitle));
        this.jsonItems = menuData;
        if (parent == null) datapath = new ArrayList<>();
        circleEntries = Config.get().minemenuFabric.menuEntries;
        outerRadius = Config.get().minemenuFabric.outerRadius;
        innerRadius = Config.get().minemenuFabric.innerRadius;
    }

    public void tick() {
        if (repeatButton.isHovered() && repeatData != null) {
            repeatButton.setMessage(Text.of(repeatData.get("name").getAsString()));
        }
        else if (repeatButton.isHovered() && repeatData == null)  {
            repeatButton.setMessage(new TranslatableText("minemenu.gui.noRepeat"));
        }
        else repeatButton.setMessage(new TranslatableText("minemenu.gui.repeat"));
    }

    protected void init() {
        repeatButton = this.addButton(new ButtonWidget(this.width / 2 - 75, this.height - 35, 150, 20,
                new TranslatableText("minemenu.gui.repeat"), (buttonWidget) -> {
            if (repeatData != null) this.handleTypes(repeatData);
        }));
        repeatButton.visible = Config.get().minemenuFabric.repeatButton;
    }

    @Override
    public void render(MatrixStack matrices, int mouseX, int mouseY, float delta) {
        int centerX = this.client.getWindow().getScaledWidth() / 2;
        int centerY = this.client.getWindow().getScaledHeight() / 2;
        this.client.textRenderer.draw(matrices, this.title,
                centerX - this.client.textRenderer.getWidth(this.title) / 2.0F,
                centerY - outerRadius - 20,
                0xFFFFFF);

        int degrees = (int) (360.0D / circleEntries);
        int currentAngle = 360 - degrees / 2;
        int mouseAngle = (int) AngleHelper.getMouseAngle();

        for (Map.Entry<String, JsonElement> entry : jsonItems.entrySet()) {
            JsonObject value;
            try {
                value = entry.getValue().getAsJsonObject();
            } catch (Exception e) {
                continue;
            }

            int nextAngle = currentAngle + degrees;
            nextAngle = (int) AngleHelper.correctAngle(nextAngle);

            boolean mouseIn = AngleHelper.isAngleBetween(mouseAngle, currentAngle-0.1f, nextAngle-0.1f);

            boolean isHovered = !AngleHelper.isInsideCircle(mouseX, mouseY, centerX, centerY, innerRadius)
                    && AngleHelper.isInsideCircle(mouseX, mouseY, centerX, centerY,  outerRadius)
                    && mouseIn;

            int drawX = centerX-8;
            int drawY = centerY-8;

            double sin = Math.sin(Math.toRadians(currentAngle + degrees * 0.5D));
            double cos = Math.cos(Math.toRadians(currentAngle + degrees * 0.5D));

            double outerPointX = (isHovered ? outerRadius + 5 : outerRadius) * sin;
            double outerPointY = (isHovered ? outerRadius + 5 : outerRadius) * cos;
            double innerPointX = innerRadius * sin;
            double innerPointY = innerRadius * cos;
            drawX += (outerPointX + innerPointX) / 2;
            drawY -= (outerPointY + innerPointY) / 2;

            if (value.get("type").getAsString().equals("empty")) { client.getItemRenderer().renderInGui(
                    RandomUtil.itemStackFromString(Config.get().minemenuFabric.emptyItemIcon), drawX, drawY);
            }
            else {
                JsonObject iconData = value.get("icon").getAsJsonObject();
                ItemStack i;
                if (MineMenuFabricClient.playerHeadCache.containsKey(iconData.get("skullOwner").getAsString()) &&
                        !iconData.get("skullOwner").getAsString().trim().isEmpty()) {
                    client.getItemRenderer().renderInGui(playerHeadCache.get(iconData.get("skullOwner").getAsString()), drawX, drawY);
                }
                else {
                    i = RandomUtil.iconify(iconData.get("iconItem").getAsString(),
                            iconData.get("enchanted").getAsBoolean(), iconData.get("skullOwner").getAsString(),
                            iconData.get("customModelData").getAsInt());
                    if (i == null) {
                        try {
                            client.getItemRenderer().renderInGui(playerHeadCache.get(iconData.get("skullOwner").getAsString()), drawX, drawY);
                        } catch (Exception ignore) {}
                    }
                    else client.getItemRenderer().renderInGui(i, drawX, drawY);
                }
            }

            String itemName = value.get("name").getAsString().trim();
            Text itemNameText = new TranslatableText(itemName);

            int primaryColor;
            try { primaryColor = RandomUtil.getColor(Config.get().minemenuFabric.primaryColor).getColor(); }
            catch (Exception e) { primaryColor = RandomUtil.getColor("#A00000CC").getColor(); }

            int secondaryColor;
            try { secondaryColor = RandomUtil.getColor(Config.get().minemenuFabric.secondaryColor).getColor(); }
            catch (Exception e) { secondaryColor = RandomUtil.getColor("#212121D0").getColor(); }

            if (isHovered) {
                MineMenuSelectScreen.drawDoughnutSegment(matrices,
                        currentAngle, currentAngle + degrees / 2, centerX, centerY,
                        outerRadius + 5, innerRadius,
                        primaryColor);
                MineMenuSelectScreen.drawDoughnutSegment(matrices,
                        currentAngle + degrees / 2, currentAngle + degrees, centerX, centerY,
                        outerRadius + 5, innerRadius,
                        primaryColor);

                this.client.textRenderer.draw(matrices,
                        itemNameText,
                        centerX - this.client.textRenderer.getWidth(itemNameText) / 2.0F,
                        centerY + outerRadius + 10,
                        0xFFFFFF);
            } else {
                MineMenuSelectScreen.drawDoughnutSegment(matrices,
                        currentAngle,
                        currentAngle + degrees / 2, centerX, centerY,
                        outerRadius, innerRadius,
                        secondaryColor);
                MineMenuSelectScreen.drawDoughnutSegment(matrices,
                        currentAngle + degrees / 2,
                        currentAngle + degrees,
                        centerX, centerY,
                        outerRadius, innerRadius,
                        secondaryColor);
            }

            currentAngle += degrees;
            currentAngle = (int) AngleHelper.correctAngle(currentAngle);
        }
        super.render(matrices, mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (repeatButton.isMouseOver(mouseX, mouseY)) {
            if (button == 1 && repeatDatapath != null) {
                isRepeatEdit = true;
                try {
                    client.openScreen(new MineMenuSettingsScreen(this, true));
                } catch (Exception ignore) {}
                return true;
            }
            return super.mouseClicked(mouseX, mouseY, button);
        }
        int centerX = this.client.getWindow().getScaledWidth() / 2;
        int centerY = this.client.getWindow().getScaledHeight() / 2;
        if (!AngleHelper.isInsideCircle(mouseX, mouseY, centerX, centerY, innerRadius)
                && AngleHelper.isInsideCircle(mouseX, mouseY, centerX, centerY, outerRadius)) {

            int degrees = (int) (360.0D / circleEntries);
            int currentAngle = 360 - degrees / 2;
            int mouseAngle = (int) AngleHelper.getMouseAngle();

            for (Map.Entry<String, JsonElement> entry : jsonItems.entrySet()) {
                JsonObject value;
                try {
                     value = entry.getValue().getAsJsonObject();
                } catch (Exception e) {continue; }
                int nextAngle = currentAngle + degrees;
                nextAngle = (int) AngleHelper.correctAngle(nextAngle);
                boolean mouseIn = AngleHelper.isAngleBetween(mouseAngle, currentAngle-0.1f, nextAngle-0.1f);
                if (mouseIn) {
                    datapath.add(entry.getKey());
                    if (button == 0) this.handleTypes(value);
                    else if (button == 1) RandomUtil.openConfigScreen(this);
                }
                currentAngle += degrees;
                currentAngle = (int) AngleHelper.correctAngle(currentAngle);
            }
        }
        else {
            this.client.openScreen(null);
        }
        return false;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }


    public static void drawDoughnutSegment(MatrixStack matrixStack, int startingAngle, int endingAngle, float centerX,
                                           float centerY, double outerRingRadius, double innerRingRadius, int color) {
        float f = (float) (color >> 24 & 0xff) / 255F;
        float f1 = (float) (color >> 16 & 0xff) / 255F;
        float f2 = (float) (color >> 8 & 0xff) / 255F;
        float f3 = (float) (color & 0xff) / 255F;
        matrixStack.push();
        RenderSystem.enableBlend();
        RenderSystem.disableTexture();
        RenderSystem.defaultBlendFunc();
        BufferBuilder bufferBuilder = Tessellator.getInstance().getBuffer();
        bufferBuilder.begin(GL11.GL_TRIANGLE_FAN, VertexFormats.POSITION_COLOR);
        for (int i = startingAngle; i <= endingAngle; i++) {
            double x = Math.sin(Math.toRadians(i)) * innerRingRadius;
            double y = Math.cos(Math.toRadians(i)) * innerRingRadius;
            bufferBuilder.vertex(centerX + x, centerY - y, 0).color(f1, f2, f3, f).next();
        }
        for (int i = endingAngle; i >= startingAngle; i--) {
            double x = Math.sin(Math.toRadians(i)) * outerRingRadius;
            double y = Math.cos(Math.toRadians(i)) * outerRingRadius;
            bufferBuilder.vertex(centerX + x, centerY - y, 0).color(f1, f2, f3, f).next();
        }
        bufferBuilder.end();
        BufferRenderer.draw(bufferBuilder);
        RenderSystem.enableTexture();
        RenderSystem.disableBlend();
        matrixStack.pop();
    }

    private void handleTypes(JsonObject value) {
        System.out.println("-----------------------");
        System.out.println(datapath);
        System.out.println(repeatDatapath);
        System.out.println("-____________________________");

        String type = value.get("type").getAsString();
        updateRepeatData(type, value);

        switch (type) {
            case "empty":
                RandomUtil.openConfigScreen(this);
                break;

            case "category":
                datapath.add("data");
                GsonUtil.saveJson(GsonUtil.fixEntryAmount(value.get("data").getAsJsonObject()));

                client.openScreen(new MineMenuSelectScreen(value.get("data").getAsJsonObject(),
                        value.get("name").getAsString(), this));
                break;


            case "print":
                client.player.sendChatMessage(value.get("data").getAsString());
                this.client.openScreen(null);
                break;

            case "chatbox":
                client.openScreen(new ChatScreen(value.get("data").getAsString()));
                break;

            case "clipboard":
                this.client.keyboard.setClipboard(value.get("data").getAsString());
                client.player.sendMessage(new TranslatableText("minemenu.select.copied"), true);
                this.client.openScreen(null);
                break;

            case "keybinding":
                if (client.currentScreen instanceof MineMenuSelectScreen) this.client.openScreen(null);
                InputUtil.Key key = InputUtil.fromTranslationKey(value.get("data").getAsJsonObject().get("key").getAsString());
                int releaseDelay = value.get("data").getAsJsonObject().get("releaseDelay").getAsInt();
                Thread press = new Thread(() -> {
                    KeyBinding.setKeyPressed(key, true);
                    try { Thread.sleep(releaseDelay); } catch (InterruptedException e) { e.printStackTrace(); }
                    KeyBinding.setKeyPressed(key, false);
                });
                press.start();
                break;

            case "link":
                String link = value.get("data").getAsString();
                try {
                    if (!link.startsWith("http")) link = "http://" + link;
                    new URL(link);
                    new URI(link);
                    Util.getOperatingSystem().open(link);
                } catch (Exception e) {
                    client.player.sendMessage(new TranslatableText("minemenu.error.link"), true);
                }
                break;
        }
    }

    @SuppressWarnings("unchecked")
    public static void updateRepeatData(String type, JsonObject value) {
        if (!type.equals("category")) {
            repeatData = value;
            if (!datapath.isEmpty()) {
                repeatDatapath = (ArrayList<String>) datapath.clone();
            }
        }

    }
}