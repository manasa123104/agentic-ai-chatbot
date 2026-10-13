"""Builds BOT QR codes and a printable poster (poster/bot-poster.png)."""
from pathlib import Path

import qrcode
from PIL import Image, ImageDraw, ImageFont

OUT = Path(__file__).resolve().parent
GITHUB_URL = "https://github.com/manasa123104/agentic-ai-chatbot"
CONVERTER_URL = "http://192.168.1.153:8080/convert"

W, H = 1800, 2400
FONTS = Path("C:/Windows/Fonts")


def font(name, size):
    try:
        return ImageFont.truetype(str(FONTS / name), size)
    except OSError:
        return ImageFont.load_default()


def make_qr(url, path, color="#111827"):
    qr = qrcode.QRCode(error_correction=qrcode.constants.ERROR_CORRECT_H, box_size=16, border=2)
    qr.add_data(url)
    qr.make(fit=True)
    img = qr.make_image(fill_color=color, back_color="white").convert("RGB")
    img.save(path)
    return img


def gradient(w, h, top, bottom):
    img = Image.new("RGB", (w, h))
    d = ImageDraw.Draw(img)
    for y in range(h):
        t = y / (h - 1)
        d.line([(0, y), (w, y)], fill=tuple(int(top[i] + (bottom[i] - top[i]) * t) for i in range(3)))
    return img


def centered(d, y, text, f, fill):
    x = (W - d.textlength(text, font=f)) / 2
    d.text((x, y), text, font=f, fill=fill)


def main():
    qr_git = make_qr(GITHUB_URL, OUT / "qr-github.png")
    qr_conv = make_qr(CONVERTER_URL, OUT / "qr-converter.png")

    img = gradient(W, H, (15, 23, 42), (49, 46, 129))
    d = ImageDraw.Draw(img)

    title = font("segoeuib.ttf", 260)
    h1 = font("segoeuib.ttf", 64)
    h2 = font("segoeuib.ttf", 48)
    body = font("segoeui.ttf", 40)
    small = font("segoeui.ttf", 32)
    accent = (129, 140, 248)
    soft = (203, 213, 225)

    centered(d, 90, "BOT", title, "white")
    centered(d, 400, "From a prompt to a Java website - and code in any language", h2, accent)

    # Five-bot pipeline
    centered(d, 540, "5 bots, one pipeline", h1, "white")
    bots = ["Business Analyst", "Java Developer", "Java Unit Tester", "Functional Testing", "Developer Tester"]
    bw, bh, gap = 300, 190, 40
    x0 = (W - (5 * bw + 4 * gap)) / 2
    y0 = 650
    for i, name in enumerate(bots):
        x = x0 + i * (bw + gap)
        d.rounded_rectangle([x, y0, x + bw, y0 + bh], radius=28, fill=(30, 41, 59), outline=accent, width=4)
        d.text((x + 24, y0 + 18), str(i + 1), font=h2, fill=accent)
        words = name.split(" ", 1)
        d.text((x + 24, y0 + 90), words[0], font=small, fill="white")
        if len(words) > 1:
            d.text((x + 24, y0 + 130), words[1], font=small, fill="white")
        if i < 4:
            ax = x + bw + 6
            ay = y0 + bh / 2
            d.polygon([(ax, ay - 12), (ax + gap - 12, ay), (ax, ay + 12)], fill=accent)

    # Features
    features = [
        "Type a prompt  ->  user stories + full Java website + tests",
        "View the generated Java code and workflow diagram",
        "Convert code between 15 languages (Java, Python, C#, Go, COBOL ...)",
        "Sample programs in every language: Hello World, Factorial, Binary Search, FizzBuzz",
    ]
    fy = 920
    for line in features:
        d.ellipse([170, fy + 16, 190, fy + 36], fill=accent)
        d.text((220, fy), line, font=body, fill=soft)
        fy += 72

    # QR panels
    centered(d, 1250, "Scan to try it", h1, "white")
    size = 560
    panels = [
        (qr_git, "Source code on GitHub", "github.com/manasa123104"),
        (qr_conv, "Live code converter", "Same Wi-Fi: 192.168.1.153:8080/convert"),
    ]
    px_gap = 140
    px0 = (W - (2 * (size + 80) + px_gap)) / 2
    for i, (qr_img, label, caption) in enumerate(panels):
        px = px0 + i * (size + 80 + px_gap)
        py = 1370
        d.rounded_rectangle([px, py, px + size + 80, py + size + 230], radius=36, fill="white")
        img.paste(qr_img.resize((size, size), Image.NEAREST), (int(px + 40), py + 40))
        lw = d.textlength(label, font=h2)
        d.text((px + (size + 80 - lw) / 2, py + size + 60), label, font=h2, fill=(17, 24, 39))
        cw = d.textlength(caption, font=small)
        d.text((px + (size + 80 - cw) / 2, py + size + 135), caption, font=small, fill=(71, 85, 105))

    centered(d, 2250, "Spring Boot  -  Java 17  -  Local AI (Ollama) or OpenAI", small, soft)

    img.save(OUT / "bot-poster.png")
    print("Wrote", OUT / "bot-poster.png")


if __name__ == "__main__":
    main()
