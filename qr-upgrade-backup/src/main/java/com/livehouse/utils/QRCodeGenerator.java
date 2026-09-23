package com.livehouse.utils;

import com.google.zxing.*;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import lombok.extern.slf4j.Slf4j;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * 二维码生成和解析工具类
 */
@Slf4j
public class QRCodeGenerator {

    private static final int DEFAULT_WIDTH = 300;
    private static final int DEFAULT_HEIGHT = 300;

    /**
     * 生成二维码图片（返回Base64编码）
     *
     * @param content 二维码内容
     * @return Base64编码的图片字符串
     */
    public static String generateQRCodeAsBase64(String content) {
        return generateQRCodeAsBase64(content, DEFAULT_WIDTH, DEFAULT_HEIGHT);
    }

    /**
     * 生成二维码图片（返回Base64编码）
     *
     * @param content 二维码内容
     * @param width   图片宽度
     * @param height  图片高度
     * @return Base64编码的图片字符串
     */
    public static String generateQRCodeAsBase64(String content, int width, int height) {
        try {
            QRCodeWriter qrCodeWriter = new QRCodeWriter();
            
            // 设置二维码参数
            Map<EncodeHintType, Object> hints = new HashMap<>();
            hints.put(EncodeHintType.CHARACTER_SET, "UTF-8");
            hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M);
            hints.put(EncodeHintType.MARGIN, 1);

            // 生成二维码矩阵
            BitMatrix bitMatrix = qrCodeWriter.encode(content, BarcodeFormat.QR_CODE, width, height, hints);
            
            // 转换为BufferedImage
            BufferedImage bufferedImage = MatrixToImageWriter.toBufferedImage(bitMatrix);
            
            // 转换为Base64
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(bufferedImage, "PNG", baos);
            byte[] imageBytes = baos.toByteArray();
            
            return java.util.Base64.getEncoder().encodeToString(imageBytes);
            
        } catch (WriterException | IOException e) {
            log.error("生成二维码失败，内容：{}", content, e);
            throw new RuntimeException("生成二维码失败", e);
        }
    }

    /**
     * 生成二维码并保存到文件
     *
     * @param content  二维码内容
     * @param filePath 保存路径
     * @return 是否成功
     */
    public static boolean generateQRCodeToFile(String content, String filePath) {
        return generateQRCodeToFile(content, filePath, DEFAULT_WIDTH, DEFAULT_HEIGHT);
    }

    /**
     * 生成二维码并保存到文件
     *
     * @param content  二维码内容
     * @param filePath 保存路径
     * @param width    图片宽度
     * @param height   图片高度
     * @return 是否成功
     */
    public static boolean generateQRCodeToFile(String content, String filePath, int width, int height) {
        try {
            QRCodeWriter qrCodeWriter = new QRCodeWriter();
            
            Map<EncodeHintType, Object> hints = new HashMap<>();
            hints.put(EncodeHintType.CHARACTER_SET, "UTF-8");
            hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M);
            hints.put(EncodeHintType.MARGIN, 1);

            BitMatrix bitMatrix = qrCodeWriter.encode(content, BarcodeFormat.QR_CODE, width, height, hints);
            MatrixToImageWriter.writeToPath(bitMatrix, "PNG", new File(filePath).toPath());
            
            return true;
        } catch (WriterException | IOException e) {
            log.error("生成二维码文件失败，内容：{}，路径：{}", content, filePath, e);
            return false;
        }
    }

    /**
     * 解析二维码内容（从Base64图片）
     *
     * @param base64Image Base64编码的图片
     * @return 二维码内容
     */
    public static String decodeQRCodeFromBase64(String base64Image) {
        try {
            // 去除Base64前缀（如果有的话）
            String imageData = base64Image;
            if (imageData.contains(",")) {
                imageData = imageData.split(",")[1];
            }
            
            byte[] imageBytes = java.util.Base64.getDecoder().decode(imageData);
            BufferedImage bufferedImage = ImageIO.read(new ByteArrayInputStream(imageBytes));
            
            return decodeQRCode(bufferedImage);
        } catch (IOException e) {
            log.error("解析Base64二维码失败", e);
            throw new RuntimeException("解析二维码失败", e);
        }
    }

    /**
     * 解析二维码内容（从文件）
     *
     * @param filePath 图片文件路径
     * @return 二维码内容
     */
    public static String decodeQRCodeFromFile(String filePath) {
        try {
            BufferedImage bufferedImage = ImageIO.read(new File(filePath));
            return decodeQRCode(bufferedImage);
        } catch (IOException e) {
            log.error("解析二维码文件失败，路径：{}", filePath, e);
            throw new RuntimeException("解析二维码失败", e);
        }
    }

    /**
     * 解析二维码内容（从BufferedImage）
     *
     * @param bufferedImage 图片对象
     * @return 二维码内容
     */
    private static String decodeQRCode(BufferedImage bufferedImage) {
        try {
            LuminanceSource source = new BufferedImageLuminanceSource(bufferedImage);
            Binarizer binarizer = new HybridBinarizer(source);
            BinaryBitmap binaryBitmap = new BinaryBitmap(binarizer);

            Map<DecodeHintType, Object> hints = new HashMap<>();
            hints.put(DecodeHintType.CHARACTER_SET, "UTF-8");

            Result result = new MultiFormatReader().decode(binaryBitmap, hints);
            return result.getText();
        } catch (NotFoundException e) {
            log.error("未找到二维码", e);
            throw new RuntimeException("图片中未找到二维码", e);
        }
    }

    /**
     * 生成电子票二维码内容（JSON格式）
     * 包含票务信息和验证数据
     *
     * @param ticketId     票务ID
     * @param ticketCode   票务编码
     * @param showId       演出ID
     * @param userId       用户ID
     * @param timestamp    时间戳
     * @param signature    数字签名
     * @return JSON格式的二维码内容
     */
    public static String generateTicketQRContent(Long ticketId, String ticketCode, Long showId, 
                                                Long userId, Long timestamp, String signature) {
        return String.format(
            "{\"ticketId\":%d,\"ticketCode\":\"%s\",\"showId\":%d,\"userId\":%d,\"timestamp\":%d,\"signature\":\"%s\"}",
            ticketId, ticketCode, showId, userId, timestamp, signature
        );
    }
}