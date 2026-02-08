package com.simpletimer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraftforge.client.ClientCommandHandler;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.InputEvent;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;
import java.io.*;
import java.util.Properties;
import java.awt.Color;

@Mod(modid = "simpletimer", version = "2.0")
public class SimpleTimerMod {
    
    public static KeyBinding toggleKey;
    public static KeyBinding resetKey;
    
    // Настройки (Сохраняются в файл)
    public static float posX = 10;
    public static float posY = 10;
    public static float scale = 1.0f;     // Размер
    public static int colorIndex = 0;     // 0=White, 1=Red, 2=Green... 99=Chroma
    public static boolean showBg = false; // Фон
    public static long savedTime = 0;     // Сохраненное время
    
    // Внутренние переменные
    private long startTime = 0;
    private boolean isRunning = false;
    private Minecraft mc;
    private FontRenderer fr;
    public static boolean shouldOpenGui = false;
    private static File configFile;

    // Цвета: Белый, Красный, Зеленый, Синий, Золотой, Фиолетовый
    private static final int[] COLORS = {0xFFFFFF, 0xFF5555, 0x55FF55, 0x5555FF, 0xFFAA00, 0xFF55FF};

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        configFile = new File(event.getModConfigurationDirectory(), "simpletimer.cfg");
        loadConfig();
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        toggleKey = new KeyBinding("Timer Pause", Keyboard.KEY_P, "Simple Timer");
        resetKey = new KeyBinding("Timer Reset", Keyboard.KEY_R, "Simple Timer");
        ClientRegistry.registerKeyBinding(toggleKey);
        ClientRegistry.registerKeyBinding(resetKey);
        ClientCommandHandler.instance.registerCommand(new TimerCommand());
        MinecraftForge.EVENT_BUS.register(this);
        mc = Minecraft.getMinecraft();
    }

    // --- УПРАВЛЕНИЕ ---
    @SubscribeEvent
    public void onKeyInput(InputEvent.KeyInputEvent event) {
        if (toggleKey.isPressed()) {
            if (isRunning) {
                savedTime += System.currentTimeMillis() - startTime;
                isRunning = false;
                saveConfig(); // Сохраняем при паузе
            } else {
                startTime = System.currentTimeMillis();
                isRunning = true;
            }
        }
        if (resetKey.isPressed()) {
            isRunning = false;
            savedTime = 0;
            startTime = 0;
            saveConfig(); // Сохраняем при сбросе
        }
    }

    // --- ОТРИСОВКА ---
    @SubscribeEvent
    public void onRenderOverlay(RenderGameOverlayEvent.Post event) {
        if (shouldOpenGui) {
            mc.displayGuiScreen(new TimerGuiScreen());
            shouldOpenGui = false;
        }

        if (event.type != RenderGameOverlayEvent.ElementType.HOTBAR) return;
        if (fr == null) fr = mc.fontRendererObj;
        if (mc.currentScreen instanceof TimerGuiScreen) return; // В меню рисует само меню

        drawTimer(posX, posY, scale);
    }

    // Метод отрисовки (используется и в игре, и в GUI)
    public static void drawTimer(float x, float y, float sc) {
        Minecraft mc = Minecraft.getMinecraft();
        FontRenderer fr = mc.fontRendererObj;
        
        long timeToDisplay = savedTime;
        // Если таймер идет, нам нужно добавить текущий прогресс, но НЕ менять savedTime
        // (savedTime меняется только при паузе)
        if (SimpleTimerMod.getInstance().isRunning) { 
             timeToDisplay += System.currentTimeMillis() - SimpleTimerMod.getInstance().startTime;
        }

        long minutes = (timeToDisplay / 60000);
        long seconds = (timeToDisplay / 1000) % 60;
        long millis = timeToDisplay % 1000;

        StringBuilder sb = new StringBuilder();
        if (minutes < 10) sb.append('0'); sb.append(minutes).append(':');
        if (seconds < 10) sb.append('0'); sb.append(seconds).append('.');
        if (millis < 100) sb.append('0');
        if (millis < 10) sb.append('0'); sb.append(millis);
        
        String text = sb.toString();

        // Магия OpenGL для размера (Scale)
        GL11.glPushMatrix();
        GL11.glTranslatef(x, y, 0);
        GL11.glScalef(sc, sc, sc);

        // Цвет
        int color;
        if (colorIndex == 99) { // Chroma (Радуга)
            long t = System.currentTimeMillis() % 3000;
            float hue = t / 3000f;
            color = Color.HSBtoRGB(hue, 1.0f, 1.0f);
        } else {
            color = COLORS[colorIndex % COLORS.length];
        }

        // Фон
        if (showBg) {
            GuiScreen.drawRect(-2, -2, fr.getStringWidth(text) + 2, 10, 0x80000000);
        }

        fr.drawStringWithShadow(text, 0, 0, color);
        GL11.glPopMatrix();
    }
    
    // Получить инстанс для статических методов
    private static SimpleTimerMod instance;
    public SimpleTimerMod() { instance = this; }
    public static SimpleTimerMod getInstance() { return instance; }

    // --- СОХРАНЕНИЕ / ЗАГРУЗКА ---
    public static void saveConfig() {
        try {
            Properties prop = new Properties();
            prop.setProperty("posX", String.valueOf(posX));
            prop.setProperty("posY", String.valueOf(posY));
            prop.setProperty("scale", String.valueOf(scale));
            prop.setProperty("color", String.valueOf(colorIndex));
            prop.setProperty("showBg", String.valueOf(showBg));
            prop.setProperty("time", String.valueOf(savedTime)); // Сохраняем время
            FileOutputStream fos = new FileOutputStream(configFile);
            prop.store(fos, "SimpleTimer Config");
            fos.close();
        } catch (Exception e) { e.printStackTrace(); }
    }

    public static void loadConfig() {
        if (!configFile.exists()) return;
        try {
            Properties prop = new Properties();
            FileInputStream fis = new FileInputStream(configFile);
            prop.load(fis);
            posX = Float.parseFloat(prop.getProperty("posX", "10"));
            posY = Float.parseFloat(prop.getProperty("posY", "10"));
            scale = Float.parseFloat(prop.getProperty("scale", "1.0"));
            colorIndex = Integer.parseInt(prop.getProperty("color", "0"));
            showBg = Boolean.parseBoolean(prop.getProperty("showBg", "false"));
            savedTime = Long.parseLong(prop.getProperty("time", "0"));
            fis.close();
        } catch (Exception e) { e.printStackTrace(); }
    }

    // --- КОМАНДЫ И GUI ---
    public static class TimerCommand extends CommandBase {
        @Override public String getCommandName() { return "timer"; }
        @Override public String getCommandUsage(ICommandSender sender) { return "/timer"; }
        @Override public int getRequiredPermissionLevel() { return 0; }
        @Override
        public void processCommand(ICommandSender sender, String[] args) {
             SimpleTimerMod.shouldOpenGui = true;
        }
    }
    
    public static class TimerGuiScreen extends GuiScreen {
        private boolean dragging = false;
        private float dragOffsetX = 0, dragOffsetY = 0;
        
        @Override
        public void drawScreen(int mouseX, int mouseY, float partialTicks) {
            this.drawDefaultBackground();
            
            // Логика перетаскивания
            if (dragging) {
                SimpleTimerMod.posX = mouseX - dragOffsetX;
                SimpleTimerMod.posY = mouseY - dragOffsetY;
            }
            
            // Рисуем сам таймер
            SimpleTimerMod.drawTimer(SimpleTimerMod.posX, SimpleTimerMod.posY, SimpleTimerMod.scale);
            
            // Рисуем рамку вокруг (с учетом масштаба)
            float w = fontRendererObj.getStringWidth("00:00.000") * SimpleTimerMod.scale;
            float h = 9 * SimpleTimerMod.scale;
            drawRect((int)SimpleTimerMod.posX - 2, (int)SimpleTimerMod.posY - 2, (int)(SimpleTimerMod.posX + w + 4), (int)(SimpleTimerMod.posY + h + 2), 0x40FFFFFF);

            // Инструкция
            drawCenteredString(fontRendererObj, "§aLeft Click§r: Drag  |  §aRight Click§r: Toggle BG", width / 2, height / 2 - 20, 0xFFFFFF);
            drawCenteredString(fontRendererObj, "§aMiddle Click§r: Change Color  |  §aScroll§r: Resize", width / 2, height / 2 - 5, 0xFFFFFF);
            drawCenteredString(fontRendererObj, "§cESC to Save & Close", width / 2, height / 2 + 25, 0xFFFFFF);
            
            super.drawScreen(mouseX, mouseY, partialTicks);
        }
        
        @Override
        public void handleMouseInput() throws IOException {
            super.handleMouseInput();
            int dWheel = Mouse.getEventDWheel();
            if (dWheel != 0) {
                if (dWheel > 0) SimpleTimerMod.scale += 0.1f;
                else SimpleTimerMod.scale -= 0.1f;
                
                if (SimpleTimerMod.scale < 0.5f) SimpleTimerMod.scale = 0.5f;
                if (SimpleTimerMod.scale > 5.0f) SimpleTimerMod.scale = 5.0f;
            }
        }

        @Override
        protected void mouseClicked(int mouseX, int mouseY, int mouseButton) {
            float w = fontRendererObj.getStringWidth("00:00.000") * SimpleTimerMod.scale;
            float h = 9 * SimpleTimerMod.scale;
            
            boolean hovered = mouseX >= SimpleTimerMod.posX && mouseX <= SimpleTimerMod.posX + w && 
                              mouseY >= SimpleTimerMod.posY && mouseY <= SimpleTimerMod.posY + h;

            if (hovered) {
                if (mouseButton == 0) { // ЛКМ - Тащить
                    dragging = true;
                    dragOffsetX = mouseX - SimpleTimerMod.posX;
                    dragOffsetY = mouseY - SimpleTimerMod.posY;
                } else if (mouseButton == 1) { // ПКМ - Фон
                    SimpleTimerMod.showBg = !SimpleTimerMod.showBg;
                    mc.getSoundHandler().playSound(net.minecraft.client.audio.PositionedSoundRecord.create(new net.minecraft.util.ResourceLocation("gui.button.press"), 1.0F));
                } else if (mouseButton == 2) { // СКМ - Цвет
                   changeColor();
                }
            } else {
                 // Если кликнули мимо таймера, но в меню - тоже можно менять цвет колесиком
                 if (mouseButton == 2) changeColor();
            }
        }
        
        private void changeColor() {
             SimpleTimerMod.colorIndex++;
             if (SimpleTimerMod.colorIndex >= 6 && SimpleTimerMod.colorIndex < 99) SimpleTimerMod.colorIndex = 99; // Включаем радугу
             else if (SimpleTimerMod.colorIndex > 99) SimpleTimerMod.colorIndex = 0; // Сброс на белый
             mc.getSoundHandler().playSound(net.minecraft.client.audio.PositionedSoundRecord.create(new net.minecraft.util.ResourceLocation("gui.button.press"), 1.0F));
        }
        
        @Override protected void mouseReleased(int mouseX, int mouseY, int state) { dragging = false; }
        
        @Override
        public void onGuiClosed() {
            SimpleTimerMod.saveConfig(); // Сохраняем все настройки при выходе
        }
        
        @Override public boolean doesGuiPauseGame() { return true; }
    }
}
