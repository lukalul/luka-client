package dev.cbui.gui;

/** Minecraft-free responsive geometry for the modules panel. */
public final class ModulesLayout {
  public final int panelX, panelY, panelWidth, panelHeight, toolbarY, contentTop;
  public final int columns, gap, inset, cardWidth, cardHeight, cardPitchX, cardPitchY;

  private ModulesLayout(
      int panelX,
      int panelY,
      int panelWidth,
      int panelHeight,
      int toolbarY,
      int contentTop,
      int columns,
      int gap,
      int inset,
      int cardWidth,
      int cardHeight,
      int cardPitchX,
      int cardPitchY) {
    this.panelX = panelX;
    this.panelY = panelY;
    this.panelWidth = panelWidth;
    this.panelHeight = panelHeight;
    this.toolbarY = toolbarY;
    this.contentTop = contentTop;
    this.columns = columns;
    this.gap = gap;
    this.inset = inset;
    this.cardWidth = cardWidth;
    this.cardHeight = cardHeight;
    this.cardPitchX = cardPitchX;
    this.cardPitchY = cardPitchY;
  }

  public static ModulesLayout fit(int width, int height) {
    int outside = width < 240 ? 4 : 6;
    int panelWidth = Math.min(370, Math.max(180, width - outside * 2));
    // The whole block (logo, toolbar, panel) is centred on tall windows; the panel height is capped so it never stretches to the bottom edge.
    int panelH = Math.max(64, Math.min(400, height - outside - 59));
    int toolbar = height < 150 ? 5 : Math.max(26, (height - 33 - panelH) / 2);
    int panelY = height < 150 ? 31 : toolbar + 33;
    int bottom = Math.max(panelY + 70, height < 150 ? height - outside : panelY + panelH);
    if (bottom > height - outside) bottom = height - outside;
    int panelHeight = Math.max(64, bottom - panelY);
    if (panelY + panelHeight > height - outside)
      panelY = Math.max(31, height - outside - panelHeight);
    int inset = 4, gap = 4, columns = panelWidth >= 276 ? 3 : panelWidth >= 184 ? 2 : 1;
    int cardWidth = (panelWidth - inset * 2 - gap * (columns - 1)) / columns;
    int cardHeight = Math.max(88, Math.min(108, cardWidth * 108 / 116));
    return new ModulesLayout(
        (width - panelWidth) / 2,
        panelY,
        panelWidth,
        panelHeight,
        toolbar,
        panelY + 30,
        columns,
        gap,
        inset,
        cardWidth,
        cardHeight,
        cardWidth + gap,
        cardHeight + gap);
  }

  public int cardX(int index) {
    return panelX + inset + (index % columns) * cardPitchX;
  }

  public int cardY(int index) {
    return contentTop + inset + (index / columns) * cardPitchY;
  }

  public int rowsFor(int count) {
    return (count + columns - 1) / columns;
  }

  public int contentHeight(int count) {
    return inset + rowsFor(count) * cardPitchY;
  }

  public boolean valid(int screenWidth, int screenHeight) {
    return panelX >= 0
        && panelY >= 0
        && panelX + panelWidth <= screenWidth
        && panelY + panelHeight <= screenHeight
        && columns >= 1
        && cardWidth >= 70
        && cardX(columns - 1) + cardWidth <= panelX + panelWidth;
  }
}
