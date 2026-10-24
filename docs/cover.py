from pathlib import Path
from math import cos, sin, pi
from html import escape
from PIL import Image, ImageDraw, ImageFont
from fontTools.ttLib import TTFont
from fontTools.pens.svgPathPen import SVGPathPen

ROOT = Path(__file__).parent
NAVY = '#091b22'
CORAL = '#ff7955'
MINT = '#bbf7ce'
CREAM = '#ecf0dc'
GRID = '#173139'
FONT_DIR = Path('/System/Library/Fonts/Supplemental')
FONTS = {
  'title': FONT_DIR/'Arial Bold Italic.ttf',
  'bold': FONT_DIR/'Arial Bold.ttf',
  'mono': FONT_DIR/'Andale Mono.ttf',
}
S = 3
im = Image.new('RGB', (630*S, 500*S), NAVY)
d = ImageDraw.Draw(im)
svg = ['<svg xmlns="http://www.w3.org/2000/svg" width="630" height="500" viewBox="0 0 630 500">', '<title>BANKSHOT // ZERO</title>', '<desc>Typographic arcade cover. A mint projectile ricochets around a geometric arena toward a coral drone.</desc>']

def poly(points, fill=None, stroke=None, width=1):
  pts = ' '.join(f'{x},{y}' for x,y in points)
  svg.append(f'<polygon points="{pts}" fill="{fill or "none"}" stroke="{stroke or "none"}" stroke-width="{width}" stroke-linejoin="miter"/>')
  scaled=[(round(x*S),round(y*S)) for x,y in points]
  if fill: d.polygon(scaled, fill=fill)
  if stroke: d.line(scaled+[scaled[0]], fill=stroke,width=round(width*S),joint='curve')

def line(points, color, width=1):
  pts=' '.join(f'{x},{y}' for x,y in points)
  svg.append(f'<polyline points="{pts}" fill="none" stroke="{color}" stroke-width="{width}" stroke-linecap="square" stroke-linejoin="miter"/>')
  d.line([(round(x*S),round(y*S)) for x,y in points], fill=color,width=max(1,round(width*S)),joint='curve')

def rect(x,y,w,h,color,stroke=None,width=1):
  poly([(x,y),(x+w,y),(x+w,y+h),(x,y+h)],color,stroke,width)

def circle(x,y,r,color,stroke=None,width=1):
  svg.append(f'<circle cx="{x}" cy="{y}" r="{r}" fill="{color or "none"}" stroke="{stroke or "none"}" stroke-width="{width}"/>')
  d.ellipse(((x-r)*S,(y-r)*S,(x+r)*S,(y+r)*S),fill=color,outline=stroke,width=round(width*S))

def text(value,x,baseline,size,color,face='bold',tracking=0):
  path=FONTS[face]
  font=TTFont(str(path))
  glyphset=font.getGlyphSet()
  cmap=font.getBestCmap()
  scale=size/font['head'].unitsPerEm
  cursor=x
  pilfont=ImageFont.truetype(str(path),round(size*S))
  for ch in value:
    glyph=cmap.get(ord(ch),'space')
    pen=SVGPathPen(glyphset)
    glyphset[glyph].draw(pen)
    svg.append(f'<path d="{pen.getCommands()}" transform="translate({cursor},{baseline}) scale({scale},-{scale})" fill="{color}"/>')
    d.text((round(cursor*S),round(baseline*S)),ch,font=pilfont,fill=color,anchor='ls')
    cursor += glyphset[glyph].width*scale+tracking
  font.close()
  return cursor

rect(0,0,630,500,NAVY)
# Restrained technical grid, broken by the typographic stack.
for x in range(374, 631, 28): line([(x,70),(x,389)],GRID,.7)
for y in range(80,390,28): line([(366,y),(630,y)],GRID,.7)
line([(34,63),(596,63)],GRID,1)
text('EVERY WALL IS A WEAPON',35,40,12,MINT,'mono',1.0)
text('01 / 08',519,40,12,CREAM,'mono',.1)
# Typographic stack, set as paths for a portable SVG.
text('BANK',34,167,95,CORAL,'title',-3.4)
text('SHOT.',34,263,95,CREAM,'title',-4.0)
text('// ZERO',39,314,34,MINT,'bold',-.7)
text('A RICOCHET SURVIVAL ARCADE',39,345,9.2,CREAM,'mono',.35)
line([(39,367),(317,367)],GRID,1)
text('JAVA BUILT. WALL TESTED.',39,385,8.6,CORAL,'mono',.6)
# Arena walls and clipped corners.
line([(390,82),(585,82),(602,99),(602,349),(583,368),(371,368),(371,97),(386,82)],GRID,2)
line([(428,82),(585,82),(602,99),(602,173)],CORAL,5)
line([(602,215),(602,349),(583,368),(488,368)],CORAL,5)
line([(371,291),(371,150)],CORAL,5)
# Cover, obstacle, and target bracket.
rect(472,282,33,11,NAVY,GRID,2)
rect(399,219,14,47,GRID)
line([(416,108),(444,108),(444,117)],CORAL,1)
line([(410,158),(410,169),(439,169)],CORAL,1)
# Mint projectile path with two sharp banks.
line([(387,345),(600,169),(495,82),(427,138)],MINT,4)
line([(387,345),(413,323)],CREAM,8)
for x,y in [(600,169),(495,82)]:
  circle(x,y,6,NAVY,MINT,2)
  line([(x-9,y-9),(x-15,y-15)],MINT,1)
  line([(x+8,y+9),(x+14,y+15)],MINT,1)
# Geometric hostile drone.
poly([(427,119),(448,138),(427,157),(406,138)],CORAL)
poly([(427,126),(440,138),(427,150),(414,138)],NAVY)
poly([(427,131),(434,138),(427,145),(420,138)],CREAM)
line([(413,121),(406,113)],CORAL,3)
line([(443,155),(450,163)],CORAL,3)
# Player launch mark.
circle(387,345,15,NAVY,MINT,1)
poly([(379,352),(383,331),(398,345)],MINT)
text('BANK x2',503,329,10,MINT,'mono',.8)
# Bottom strip anchors the layout.
rect(0,407,630,93,CORAL)
text('BOUNCE. DODGE. REPEAT.',35,443,23,NAVY,'bold',-.45)
text('8 WAVES  /  1 ARENA',37,477,11,NAVY,'mono',1.1)
text('PLAY FREE',503,477,11,NAVY,'bold',.4)
line([(35,454),(596,454)],NAVY,1)
svg.append('</svg>')
(ROOT/'cover.svg').write_text('\n'.join(svg))
im.resize((630,500),Image.Resampling.LANCZOS).save(ROOT/'cover.png',optimize=True)
# Social card preserves the full cover in a wide lockup.
social=Image.new('RGB',(1200,630),NAVY)
cover=im.resize((630,500),Image.Resampling.LANCZOS)
social.paste(cover,(44,65))
sd=ImageDraw.Draw(social)
sd.line([(718,65),(718,565)],fill=CORAL,width=3)
def social_text(v,xy,size,color,face='bold'):
  sd.text(xy,v,font=ImageFont.truetype(str(FONTS[face]),size),fill=color)
social_text('MAKE', (762,99),60,CREAM,'title')
social_text('EVERY', (762,164),60,CREAM,'title')
social_text('ANGLE', (762,229),60,CREAM,'title')
social_text('COUNT.', (762,294),60,MINT,'title')
social_text('BANKSHOT // ZERO',(765,417),23,CORAL)
social_text('A ricochet survival arcade',(766,461),17,CREAM,'mono')
social_text('JAVA  /  BROWSER  /  FREE',(766,513),15,MINT,'mono')
social.save(ROOT/'social-card.png',optimize=True)
print('Created cover.svg, cover.png (630 x 500), social-card.png (1200 x 630)')
