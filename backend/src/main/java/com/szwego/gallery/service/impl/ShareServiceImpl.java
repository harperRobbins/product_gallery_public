package com.szwego.gallery.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.szwego.gallery.common.BusinessException;
import com.szwego.gallery.domain.ShareLink;
import com.szwego.gallery.dto.ProductDetailVO;
import com.szwego.gallery.dto.ShareInfoVO;
import com.szwego.gallery.mapper.ShareLinkMapper;
import com.szwego.gallery.service.ProductService;
import com.szwego.gallery.service.ShareService;
import com.szwego.gallery.util.LanguageUtil;
import com.szwego.gallery.util.QrCodeUtil;
import com.szwego.gallery.util.ShortCodeUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ShareServiceImpl implements ShareService {

    private final ShareLinkMapper shareLinkMapper;
    private final ProductService productService;

    @Value("${app.share.base-url:http://localhost:8080}")
    private String shareBaseUrl;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ShareInfoVO createShare(Long productId) {
        productService.detail(productId, false, LanguageUtil.DEFAULT_LANG);

        ShareLink link = shareLinkMapper.selectOne(new LambdaQueryWrapper<ShareLink>()
                .eq(ShareLink::getProductId, productId)
                .orderByDesc(ShareLink::getId)
                .last("limit 1"));

        if (link == null) {
            link = new ShareLink();
            link.setProductId(productId);
            link.setLongUrl(shareBaseUrl + "/product/" + productId);
            link.setVisitCount(0);
            link.setShortCode(generateUniqueCode());
            shareLinkMapper.insert(link);
        }

        ShareInfoVO vo = new ShareInfoVO();
        vo.setShortCode(link.getShortCode());
        vo.setLongUrl(link.getLongUrl());
        vo.setShortUrl(shareBaseUrl + "/s/" + link.getShortCode());
        vo.setPosterUrl(shareBaseUrl + "/api/share/poster/" + productId);
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String resolveLongUrl(String shortCode) {
        ShareLink link = shareLinkMapper.selectOne(new LambdaQueryWrapper<ShareLink>()
                .eq(ShareLink::getShortCode, shortCode)
                .last("limit 1"));
        if (link == null) {
            throw new BusinessException("短链不存在");
        }
        shareLinkMapper.update(null, new LambdaUpdateWrapper<ShareLink>()
                .setSql("visit_count = visit_count + 1")
                .eq(ShareLink::getId, link.getId()));
        return link.getLongUrl();
    }

    @Override
    public byte[] generatePoster(Long productId) throws IOException {
        ProductDetailVO product = productService.detail(productId, false, LanguageUtil.DEFAULT_LANG);
        ShareInfoVO shareInfo = createShare(productId);

        int width = 1080;
        int height = 1920;
        BufferedImage canvas = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = canvas.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setPaint(new Color(250, 250, 250));
            g.fillRect(0, 0, width, height);

            g.setColor(new Color(35, 35, 35));
            g.setFont(new Font("SansSerif", Font.BOLD, 48));
            g.drawString("商品分享", 70, 90);

            drawCoverImage(g, product.getCoverImage(), width);

            g.setColor(new Color(20, 20, 20));
            g.setFont(new Font("SansSerif", Font.BOLD, 44));
            int y = 910;
            for (String line : splitLines(product.getTitle(), 16, 2)) {
                g.drawString(line, 70, y);
                y += 58;
            }

            g.setColor(new Color(217, 72, 15));
            g.setFont(new Font("SansSerif", Font.BOLD, 56));
            g.drawString("¥ " + product.getPrice(), 70, y + 30);

            g.setColor(new Color(102, 102, 102));
            g.setFont(new Font("SansSerif", Font.PLAIN, 30));
            g.drawString("货号: " + product.getSku(), 70, y + 90);

            BufferedImage qr = QrCodeUtil.generate(shareInfo.getShortUrl(), 320, 320);
            g.drawImage(qr, 70, 1450, null);
            g.setColor(new Color(40, 40, 40));
            g.setFont(new Font("SansSerif", Font.PLAIN, 34));
            g.drawString("扫码查看商品详情", 430, 1600);
            g.setFont(new Font("SansSerif", Font.PLAIN, 24));
            g.setColor(new Color(130, 130, 130));
            g.drawString(shareInfo.getShortUrl(), 430, 1650);
        } finally {
            g.dispose();
        }

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        ImageIO.write(canvas, "png", outputStream);
        return outputStream.toByteArray();
    }

    private void drawCoverImage(Graphics2D g, String coverImage, int canvasWidth) {
        if (coverImage == null || coverImage.trim().isEmpty()) {
            return;
        }
        try {
            String finalUrl = coverImage.startsWith("http://") || coverImage.startsWith("https://")
                    ? coverImage
                    : shareBaseUrl + coverImage;
            BufferedImage source = ImageIO.read(new URL(finalUrl));
            if (source == null) {
                return;
            }
            int targetW = canvasWidth - 140;
            int targetH = 760;
            Image scaled = source.getScaledInstance(targetW, targetH, Image.SCALE_SMOOTH);
            g.drawImage(scaled, 70, 120, null);
        } catch (Exception ignored) {
        }
    }

    private List<String> splitLines(String text, int maxChars, int maxLines) {
        List<String> lines = new ArrayList<String>();
        if (text == null || text.trim().isEmpty()) {
            lines.add("未命名商品");
            return lines;
        }
        String clean = text.trim();
        int from = 0;
        while (from < clean.length() && lines.size() < maxLines) {
            int to = Math.min(clean.length(), from + maxChars);
            lines.add(clean.substring(from, to));
            from = to;
        }
        if (from < clean.length() && !lines.isEmpty()) {
            String last = lines.get(lines.size() - 1);
            lines.set(lines.size() - 1, last.substring(0, Math.max(0, last.length() - 1)) + "…");
        }
        return lines;
    }

    private String generateUniqueCode() {
        for (int i = 0; i < 12; i++) {
            String code = ShortCodeUtil.randomCode(6);
            Long count = shareLinkMapper.selectCount(new LambdaQueryWrapper<ShareLink>()
                    .eq(ShareLink::getShortCode, code));
            if (count == null || count == 0L) {
                return code;
            }
        }
        throw new BusinessException("短链生成失败，请重试");
    }
}
