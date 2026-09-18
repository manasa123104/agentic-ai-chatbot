package com.agentic.chatbot.model;

/**
 * Editable site theme — defaults match Google / company light UI.
 */
public class SiteStyle {
    private String pageTitle = "Sign in";
    private String heading = "Sign in";
    private String subtitle = "to continue to your workspace";
    private String backgroundColor = "#f0f4f9";
    private String cardColor = "#ffffff";
    private String accentColor = "#1a73e8";
    private String textColor = "#202124";
    private String footerColor = "#f8f9fa";
    private String footerText = "© 2026 Company · Privacy · Terms";
    private String fontFamily = "\"Google Sans\", Roboto, Arial, sans-serif";
    private String fontSize = "16px";
    private String headingSize = "24px";
    private String logoUrl = "";
    private String heroImageUrl = "";
    private String backgroundImageUrl = "";
    private boolean showGoogleSignIn = true;
    private boolean showFooter = true;

    public String getPageTitle() { return pageTitle; }
    public void setPageTitle(String pageTitle) { this.pageTitle = pageTitle; }
    public String getHeading() { return heading; }
    public void setHeading(String heading) { this.heading = heading; }
    public String getSubtitle() { return subtitle; }
    public void setSubtitle(String subtitle) { this.subtitle = subtitle; }
    public String getBackgroundColor() { return backgroundColor; }
    public void setBackgroundColor(String backgroundColor) { this.backgroundColor = backgroundColor; }
    public String getCardColor() { return cardColor; }
    public void setCardColor(String cardColor) { this.cardColor = cardColor; }
    public String getAccentColor() { return accentColor; }
    public void setAccentColor(String accentColor) { this.accentColor = accentColor; }
    public String getTextColor() { return textColor; }
    public void setTextColor(String textColor) { this.textColor = textColor; }
    public String getFooterColor() { return footerColor; }
    public void setFooterColor(String footerColor) { this.footerColor = footerColor; }
    public String getFooterText() { return footerText; }
    public void setFooterText(String footerText) { this.footerText = footerText; }
    public String getFontFamily() { return fontFamily; }
    public void setFontFamily(String fontFamily) { this.fontFamily = fontFamily; }
    public String getFontSize() { return fontSize; }
    public void setFontSize(String fontSize) { this.fontSize = fontSize; }
    public String getHeadingSize() { return headingSize; }
    public void setHeadingSize(String headingSize) { this.headingSize = headingSize; }
    public String getLogoUrl() { return logoUrl; }
    public void setLogoUrl(String logoUrl) { this.logoUrl = logoUrl; }
    public String getHeroImageUrl() { return heroImageUrl; }
    public void setHeroImageUrl(String heroImageUrl) { this.heroImageUrl = heroImageUrl; }
    public String getBackgroundImageUrl() { return backgroundImageUrl; }
    public void setBackgroundImageUrl(String backgroundImageUrl) { this.backgroundImageUrl = backgroundImageUrl; }
    public boolean isShowGoogleSignIn() { return showGoogleSignIn; }
    public void setShowGoogleSignIn(boolean showGoogleSignIn) { this.showGoogleSignIn = showGoogleSignIn; }
    public boolean isShowFooter() { return showFooter; }
    public void setShowFooter(boolean showFooter) { this.showFooter = showFooter; }
}
