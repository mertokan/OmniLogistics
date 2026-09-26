# -*- coding: utf-8 -*-
"""The CurseForge project page, in both languages, with the item list taken straight from the lang files so a rename
never leaves the description lying. Run: python tools/gen_press.py -> docs/curseforge-en.md, docs/curseforge-tr.md
"""
import io, json, os

HERE = os.path.dirname(os.path.abspath(__file__))
LANG = os.path.join(HERE, '..', 'src', 'main', 'resources', 'assets', 'omnilogistics', 'lang')
DOCS = os.path.join(HERE, '..', 'docs')

ORDER = [
    ("Conduits", ["basic_item_pipe", "advanced_item_pipe", "elite_item_pipe", "ultimate_item_pipe", "infinity_item_pipe",
                  "basic_energy_cable", "advanced_energy_cable", "elite_energy_cable", "ultimate_energy_cable", "infinity_energy_cable",
                  "basic_fluid_pipe", "advanced_fluid_pipe", "elite_fluid_pipe", "ultimate_fluid_pipe", "infinity_fluid_pipe"]),
    ("Machines", ["wireless_router", "batch_distributor", "component_extractor", "inventory_exposer", "machine_monitor",
                  "basic_void_miner", "advanced_void_miner", "elite_void_miner", "ultimate_void_miner"]),
    ("Cards", ["logistics_card", "advanced_logistics_card", "elite_logistics_card", "ultimate_logistics_card",
               "energy_card", "fluid_card", "stock_card", "vacuum_card", "void_card", "detector_card",
               "activator_card", "breaker_card", "placer_card"]),
    ("Upgrades and modules", ["speed_upgrade", "range_upgrade", "parallel_upgrade", "chunk_upgrade", "gem_module",
                              "fusion_module", "wrench"]),
]
SECTION_TR = {"Conduits": "Hatlar", "Machines": "Makineler", "Cards": "Kartlar",
              "Upgrades and modules": "Yükseltmeler ve modüller"}
HEAD = {"en": ("Item", "What it does"), "tr": ("Eşya", "Ne yapar")}


def table(lang, code):
    out = []
    for title, ids in ORDER:
        out.append("### " + (title if code == 'en' else SECTION_TR[title]))
        out.append("")
        out.append("| %s | %s |" % HEAD[code])
        out.append("| --- | --- |")
        for i in ids:
            name = lang.get("block.omnilogistics." + i) or lang.get("item.omnilogistics." + i)
            if not name:
                continue
            desc = lang.get("desc.omnilogistics." + i, "").split("\n")[0]
            out.append("| **%s** | %s |" % (name, desc))
        out.append("")
    return "\n".join(out)


SHOTS = [
    ("omni_press_hero.png", "The whole test site: every block of the mod plus four other mods, all fed by cards.",
     "Tüm test alanı: modun her bloğu ve dört başka mod, hepsi kartlarla besleniyor."),
    ("omni_press_pipes.png", "Conduits, the Inventory Exposer, the Component Extractor and a Wireless Router.",
     "Hatlar, Envanter Yansıtıcı, Bileşen Ayırıcı ve Kablosuz Yönlendirici."),
    ("omni_press_hall.png", "One Batch Distributor feeding four machines from three different mods.",
     "Tek Toplu Dağıtıcı, üç ayrı moddan dört makineyi besliyor."),
    ("omni_press_assembler.png", "CLUSTER mode: one chest, five AE2 Molecular Assemblers, each taking a whole batch.",
     "KÜME modu: tek sandık, beş AE2 Molecular Assembler, her biri bir partiyi alıyor."),
    ("omni_press_mekanism.png", "The same shape again with five Mekanism Enrichment Chambers - and no side configuration.",
     "Aynı düzen beş Mekanism Enrichment Chamber ile - hiçbir yüz ayarı yapılmadan."),
    ("omni_press_mecraft.png", "An AE2 Pattern Provider loop that crafts on its own: nobody clicks anything.",
     "Kendi kendine craft eden AE2 Pattern Provider döngüsü: kimse hiçbir şeye tıklamıyor."),
    ("omni_press_ae2.png", "A real ME network: our pipes put items in and take them out, no AE2 parts needed.",
     "Gerçek bir ME şebekesi: borularımız eşyayı koyuyor ve alıyor, AE2 parçası gerekmiyor."),
    ("omni_press_empowerer.png", "Actually Additions: four display stands fed and powered by four cards, no cables.",
     "Actually Additions: dört stand, dört kartla besleniyor ve güçleniyor, kablo yok."),
    ("omni_press_miners.png", "Void Miners, one per tier, each with a card as its ore filter.",
     "Void Madencileri, her kademeden biri, her birinde filtre olarak bir kart."),
    ("omni_press_monitor.png", "The Machine Monitor: point a card at a machine and read it from across the room.",
     "Makine Ekranı: bir kartı makineye bağla, odanın öbür ucundan oku."),
    ("omni_press_upgrade_1st.png", "The card upgrade ritual: card in one hand, gold in the other, hold right-click.",
     "Kart yükseltme töreni: bir elde kart, diğerinde altın, sağ tıkı basılı tut."),
    ("omni_gui_router.png", "The Wireless Router: eight card slots, two upgrade slots and a tick field you can type into.",
     "Kablosuz Yönlendirici: sekiz kart yuvası, iki yükseltme yuvası ve elle yazılan tik alanı."),
    ("omni_gui_card16.png", "The card filter: 16 reference items, tags, enchantments, any data component.",
     "Kart filtresi: 16 referans eşya, etiketler, büyüler, herhangi bir veri bileşeni."),
]

EN = '''# OmniLogistics

> **CurseForge summary field (255 max):** Bind a card to any block, drop it in a pipe or a router, and OmniLogistics
> finds that machine's working face itself - on any mod's block, with no side configuration. Items, FE and fluids,
> filtered by any data component, at a tick rate you type in.

**One card system for every machine in your pack.**

Bind a Logistics Card to any block by sneak-clicking it, drop the card into a pipe, a Wireless Router or a Batch
Distributor, and OmniLogistics works out the rest. It finds the working face of that machine by itself - the input side,
the output side, the power side - on any mod's block, with no side configuration anywhere. Filter by item, by tag, by
enchantment or by any data component. Type the tick rate you want, from 1 to 200. Move items, FE and fluids, across
dimensions if you like.

Nothing in this mod is hard-coded to another mod. It talks to NeoForge capabilities only, which means it already works
with machines nobody has written yet.

## The one idea

A **Logistics Card** is a pointer to a place and a filter for what may pass.

* **Sneak + right-click** any block to bind the card to it.
* **Right-click in the air** to open the filter: reference items, tags, enchantments, damage, custom data components,
  whitelist or blacklist.
* Put it in a **conduit** and that end of the run extracts or inserts. Put it in a **Wireless Router** and the router
  reaches the block with no conduit at all. Put eight in and it serves eight places in turn.
* The card remembers **what** it is bound to, not just where: the tooltip names the block, and one key press makes that
  block flash red through the wall it is buried in.

## Auto input, auto output

Most logistics mods ask you to configure the machine: mark this face as input, that face as output, and remember it. This
mod does not ask. When a card needs to put something into a machine it probes the faces of that machine through the
capability the machine already publishes, and uses the first one that accepts the item. Same for pulling out, same for
power, same for fluids. Blocks that expose no face at all - a plain chest - are handled too.

It will not, however, reach behind a face a machine deliberately declared: an assembler's pattern slot and a crafting
output are not ours to fill.

## Tiers that say what they are

Every tier is the previous one plus a core, and the name is the core: **Gilded** (gold), **Crystal** (diamond),
**Netherforged** (netherite). The card upgrade can also be done by hand - card in one hand, the core in the other, hold
right-click and rub them together - which teaches the recipe and unlocks the crafting version for automation.

## Works with

Tested end to end, in one world, every time the mod is built: **Applied Energistics 2** (ME networks, Pattern Provider
automation, Molecular Assemblers, Interfaces, Chargers), **Mekanism** (Enrichment Chambers, Metallurgic Infusers,
Crushers, Sawmills, their cables and cubes), **Actually Additions** (Empowerer, display stands, Crusher), **Powah**,
and vanilla. Any mod that exposes the NeoForge item, energy or fluid capability works without a line of code written
for it.

**JEI** and **Patchouli** are supported when present, and neither is required.

## Getting started

1. Craft an **Item Pipe** and a **Logistics Card**.
2. Sneak + right-click a chest with the card. The tooltip now says what it is bound to.
3. Drop the card into the pipe end next to that chest: that end now pulls from it.
4. Run the pipe to a machine. Nothing to configure on the machine.
5. When one pipe is not enough, put the card in a **Wireless Router** instead and forget about the pipe.

## Requirements

Minecraft **1.21.1** (NeoForge 21.1+) or **26.1.2** (NeoForge 26.1.2+). Client and server. MIT licensed.

On 26.1.2 the in-world tests run against AE2 and Powah; Mekanism and Actually Additions have no 26.1.2 build yet, and the mod does not need either of them.

{items}
## Screenshots

{shots}
'''

TR = '''# OmniLogistics

> **CurseForge kısa açıklama alanı (en fazla 255):** Kartı bir bloğa bağla, boruya ya da yönlendiriciye koy;
> OmniLogistics makinenin çalışan yüzünü kendi bulsun - hangi modun bloğu olursa olsun, yüz ayarı yapmadan. Eşya, FE ve
> sıvı; istediğin veri bileşenine göre filtreli, yazdığın tik hızında.

**Paketindeki her makine için tek bir kart sistemi.**

Bir Lojistik Kartını herhangi bir bloğa shift+sağ tıkla bağla, kartı bir boruya, Kablosuz Yönlendiriciye ya da Toplu
Dağıtıcıya koy; gerisini OmniLogistics hallediyor. O makinenin çalışan yüzünü kendi buluyor - giriş yüzü, çıkış yüzü,
güç yüzü - hangi modun bloğu olursa olsun, hiçbir yerde yüz ayarı yapmadan. Eşyaya, etikete, büyüye ya da herhangi bir
veri bileşenine göre filtrele. İstediğin tik hızını yaz, 1'den 200'e. Eşyayı, FE'yi ve sıvıyı taşı; istersen boyutlar
arası.

Bu modda hiçbir şey başka bir moda sabit kodlanmış değil. Sadece NeoForge yetenekleriyle (capability) konuşuyor, yani
henüz yazılmamış makinelerle de şimdiden çalışıyor.

## Tek fikir

**Lojistik Kart** bir yerin adresi ve neyin geçeceğinin filtresi.

* Bağlamak için herhangi bir bloğa **shift + sağ tık**.
* Filtreyi açmak için **havaya sağ tık**: referans eşyalar, etiketler, büyüler, hasar, özel veri bileşenleri, beyaz
  liste ya da kara liste.
* **Hattın** ucuna koy, o uç çeker ya da verir. **Kablosuz Yönlendiriciye** koy, yönlendirici bloğa hiç boru olmadan
  uzanır. Sekiz kart koy, sırayla sekiz yere hizmet etsin.
* Kart **neye** bağlı olduğunu da hatırlıyor, sadece nereye değil: tooltip bloğun adını yazıyor ve tek tuşla o blok
  gömülü olduğu duvarın arkasından kırmızı yanıp sönüyor.

## Otomatik giriş, otomatik çıkış

Çoğu lojistik modu makineyi ayarlamanı ister: şu yüz giriş, bu yüz çıkış, hatırla. Bu mod sormuyor. Bir kartın makineye
bir şey koyması gerektiğinde, makinenin zaten yayınladığı yetenek üzerinden yüzlerini yokluyor ve eşyayı kabul eden ilk
yüzü kullanıyor. Çekmek için de aynı, güç için de, sıvı için de. Hiç yüz açmayan bloklar - düz bir sandık - da
çalışıyor.

Ama makinenin bilerek kapattığı yüzün arkasına uzanmıyor: bir assembler'ın pattern slotu ya da craft çıktısı bizim
doldurmamız gereken yerler değil.

## Ne olduğunu söyleyen kademeler

Her kademe bir öncekinin üstüne bir çekirdek, ismi de o çekirdek: **Yaldızlı** (altın), **Kristal** (elmas),
**Netherit** (netherit). Kart yükseltmesi elle de yapılabiliyor - bir elde kart, diğerinde çekirdek, sağ tıkı basılı
tut ve birbirine sürt - bu hem tarifi öğretiyor hem de otomasyon için craft sürümünü açıyor.

## Birlikte çalıştığı modlar

Mod her derlendiğinde tek bir dünyada baştan sona test ediliyor: **Applied Energistics 2** (ME şebekeleri, Pattern
Provider otomasyonu, Molecular Assembler, Interface, Charger), **Mekanism** (Enrichment Chamber, Metallurgic Infuser,
Crusher, Sawmill, kabloları ve küpleri), **Actually Additions** (Empowerer, standlar, Crusher), **Powah** ve vanilla.
NeoForge eşya, enerji veya sıvı yeteneğini açan her mod, onun için tek satır kod yazılmadan çalışıyor.

**JEI** ve **Patchouli** varsa destekleniyor, ikisi de zorunlu değil.

## Nasıl başlanır

1. Bir **Eşya Borusu** ve bir **Lojistik Kartı** craftla.
2. Kartla bir sandığa shift + sağ tık. Tooltip artık neye bağlı olduğunu yazıyor.
3. Kartı o sandığın yanındaki boru ucuna koy: o uç artık sandıktan çekiyor.
4. Boruyu bir makineye götür. Makinede ayarlanacak hiçbir şey yok.
5. Boru yetmediğinde kartı **Kablosuz Yönlendiriciye** koy ve boruyu unut.

## Gereksinimler

Minecraft **1.21.1** (NeoForge 21.1+) ya da **26.1.2** (NeoForge 26.1.2+). İstemci ve sunucu. MIT lisansı.

26.1.2'de oyun içi testler AE2 ve Powah ile çalışıyor; Mekanism ve Actually Additions'ın henüz 26.1.2 sürümü yok; mod ikisine de ihtiyaç duymuyor.

{items}
## Ekran görüntüleri

{shots}
'''

for code, tpl, lname in (("en", EN, "en_us"), ("tr", TR, "tr_tr")):
    lang = json.load(io.open(os.path.join(LANG, lname + '.json'), encoding='utf-8'))
    shots = "\n".join("![%s](press/%s)\n*%s*\n" % (f, f, (c_en if code == 'en' else c_tr))
                      for f, c_en, c_tr in SHOTS)
    head = "## " + ("Everything in the mod" if code == 'en' else "Modun icindeki her sey") + "\n\n"
    body = tpl.replace("{items}", head + table(lang, code)).replace("{shots}", shots)
    path = os.path.join(DOCS, 'curseforge-%s.md' % code)
    os.makedirs(DOCS, exist_ok=True)
    io.open(path, 'w', encoding='utf-8', newline='').write(body)
    print('wrote', os.path.normpath(path), len(body), 'chars')
