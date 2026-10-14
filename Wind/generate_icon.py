"""Cria ícone multirresolução para Windows sem baixar assets externos."""
from pathlib import Path
from PIL import Image, ImageDraw

SIZE = 256
S = 4
im = Image.new("RGBA", (SIZE*S, SIZE*S), (0, 0, 0, 0))
d = ImageDraw.Draw(im)
def rect(box, fill, radius=0):
    d.rounded_rectangle(tuple(int(v*S) for v in box), radius=radius*S, fill=fill)
def line(points, fill, width):
    d.line([(int(x*S), int(y*S)) for x,y in points], fill=fill, width=width*S, joint="curve")

rect((10,10,246,246), "#1158d8", 54)
rect((19,19,237,237), "#1e8df2", 48)
# folha representando transcrição
rect((142,65,222,194), "#ecf7ff", 13)
for y,w in ((107,47),(126,48),(145,45),(164,29)):
    rect((156,y,156+w,y+7), "#76b1eb", 3)
# corpo do microfone, frente
rect((55,44,143,169), "#ffffff", 42)
for x,y1,y2 in ((70,99,117),(86,85,132),(102,68,146),(118,85,132)):
    rect((x,y1,x+10,y2), "#1369e4", 5)
# suporte do microfone
d.arc((int(43*S),int(83*S),int(157*S),int(209*S)),0,180,fill="#cdeeff",width=11*S)
rect((95,194,106,219), "#ffffff", 5)
rect((65,216,137,229), "#ffffff", 7)
# seta: áudio -> texto
line([(137,170),(156,189),(185,189)], "#00dbf7", 11)
d.polygon([(185*S,175*S),(206*S,189*S),(185*S,203*S)], fill="#00dbf7")
out = Path(__file__).parent / "assets" / "audio_scriber.ico"
out.parent.mkdir(parents=True,exist_ok=True)
im.resize((256,256),Image.Resampling.LANCZOS).save(out, format="ICO",
    sizes=[(16,16),(24,24),(32,32),(48,48),(64,64),(128,128),(256,256)])
print(out)
