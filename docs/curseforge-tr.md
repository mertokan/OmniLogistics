# OmniLogistics

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

## Modun icindeki her sey

### Hatlar

| Eşya | Ne yapar |
| --- | --- |
| **Eşya Borusu** | Eşya taşır. Yüzler karşıdaki bloğa göre: NORM (bağlı), ÇEK (o bloktan alır, yeşil), VER (sadece o bloğa verir, turuncu), KAPALI. |
| **Yaldızlı Eşya Borusu** | Eşya taşır. Yüzler karşıdaki bloğa göre: NORM (bağlı), ÇEK (o bloktan alır, yeşil), VER (sadece o bloğa verir, turuncu), KAPALI. |
| **Kristal Eşya Borusu** | Eşya taşır. Yüzler karşıdaki bloğa göre: NORM (bağlı), ÇEK (o bloktan alır, yeşil), VER (sadece o bloğa verir, turuncu), KAPALI. |
| **Netherit Eşya Borusu** | Eşya taşır. Yüzler karşıdaki bloğa göre: NORM (bağlı), ÇEK (o bloktan alır, yeşil), VER (sadece o bloğa verir, turuncu), KAPALI. |
| **Omni Eşya Borusu** | Sadece yaratıcı mod. Her tick 65536 eşya taşır. |
| **Enerji Kablosu** | FE taşır. Jeneratör tarafını IN yap; bağlı diğer her şey alır. |
| **Yaldızlı Enerji Kablosu** | FE taşır. Jeneratör tarafını IN yap; bağlı diğer her şey alır. |
| **Kristal Enerji Kablosu** | FE taşır. Jeneratör tarafını IN yap; bağlı diğer her şey alır. |
| **Netherit Enerji Kablosu** | FE taşır. Jeneratör tarafını IN yap; bağlı diğer her şey alır. |
| **Omni Enerji Kablosu** | Sadece yaratıcı mod. Tick başına 16M FE. |
| **Sıvı Borusu** | Sıvı taşır. Tank tarafını IN yap; bağlı diğer her şey alır. |
| **Yaldızlı Sıvı Borusu** | Sıvı taşır. Tank tarafını IN yap; bağlı diğer her şey alır. |
| **Kristal Sıvı Borusu** | Sıvı taşır. Tank tarafını IN yap; bağlı diğer her şey alır. |
| **Netherit Sıvı Borusu** | Sıvı taşır. Tank tarafını IN yap; bağlı diğer her şey alır. |
| **Omni Sıvı Borusu** | Sadece yaratıcı mod. Tick başına 8M mB. |

### Makineler

| Eşya | Ne yapar |
| --- | --- |
| **Kablosuz Yönlendirici** | 8 kart + 2 yükseltme taşır. Bağlı bloklarla kablosuz eşya ve FE taşır. |
| **Toplu Dağıtıcı** | Altı şerit, her birine bir Lojistik Kartı: AE2 Pattern Provider tek bir pattern'i buraya iter, her malzeme kendi makinesine gider. |
| **Bileşen Ayırıcı** | Temel: büyüleri kitaba aktarır. Modüller gem çıkarma ve füzyon ekler. |
| **Envanter Yansıtıcı** | Hedef yüzündeki envanterin filtreli görünümünü sunar. |
| **Makine Ekranı** | Hangi moddan olursa olsun bir makinenin ekranı: enerji, sıvı ve ilk eşya yığınları. |
| **Void Madencisi** | Hiçlikten cevher çıkarır: FE girer, cevher çıkar. Lojistik Kart = filtre, Bant Genişliği Yükseltmesi x3 (her biri döngü başına x2 eşya ve FE), redstone durdurur. |
| **Yaldızlı Void Madencisi** | Hiçlikten cevher çıkarır: FE girer, cevher çıkar. Lojistik Kart = filtre, Bant Genişliği Yükseltmesi x3 (her biri döngü başına x2 eşya ve FE), redstone durdurur. |
| **Kristal Void Madencisi** | Hiçlikten cevher çıkarır: FE girer, cevher çıkar. Lojistik Kart = filtre, Bant Genişliği Yükseltmesi x3 (her biri döngü başına x2 eşya ve FE), redstone durdurur. |
| **Netherit Void Madencisi** | Hiçlikten cevher çıkarır: FE girer, cevher çıkar. Lojistik Kart = filtre, Bant Genişliği Yükseltmesi x3 (her biri döngü başına x2 eşya ve FE), redstone durdurur. |

### Kartlar

| Eşya | Ne yapar |
| --- | --- |
| **Lojistik Kartı** | Bağlamak için Shift + bloğa sağ tık. Ayarlamak için havaya sağ tık. |
| **Yaldızlı Lojistik Kartı** | 1 yerine 4 farklı eşyayı filtreleyen Lojistik Kartı. Lojistik Kartının çalıştığı her yerde çalışır. |
| **Kristal Lojistik Kartı** | 1 yerine 16 farklı eşyayı filtreleyen Lojistik Kartı. |
| **Netherit Lojistik Kartı** | 1 yerine 64 farklı eşyayı filtreleyen Lojistik Kartı. |
| **Enerji Kartı** | Kablosuz FE. Bağlamak için Shift + makineye sağ tık, mod değiştirmek için havaya sağ tık. |
| **Sıvı Kartı** | Bağlı tanktan sıvı ÇEKER / VERİR. |
| **Stok Kartı** | Bağlı envanterde, router'ın taşıdığı eşyadan hep bir yığın durmasını sağlar. |
| **Vakum Kartı** | Router'ın (veya bağlı bloğun) çevresindeki filtreye uyan yerdeki eşyaları tampona çeker. |
| **Yok Etme Kartı** | Router tamponuna gelen, filtreye uyan eşyaları yok eder. |
| **Algılayıcı Kart** | Bağlı envanterde filtreye uyan eşya varken router 15 redstone verir. |
| **Etkinleştirici Kart** | Tampondaki eşyayla bağlı blok yüzüne bir oyuncu gibi sağ tıklar. |
| **Kırıcı Kart** | Bağlı bloğu kırar; düşenler router tamponuna gider. |
| **Yerleştirici Kart** | Tampondaki eşyayı bağlı konuma blok olarak koyar. |

### Yükseltmeler ve modüller

| Eşya | Ne yapar |
| --- | --- |
| **Bant Genişliği Yükseltmesi** | Router: her kopya döngü başına kart işlemi sayısını 2 katına çıkarır (en fazla 3). |
| **Anten Yükseltmesi** | Router: her kopya +64 blok menzil (en fazla 3). |
| **Şerit Yükseltmesi** | Ayırıcı: her kopya bir şerit daha açar (en fazla 2), eşyalar paralel işlenir. |
| **Chunk Yükleme Yükseltmesi** | Router: sen uzaktayken kendi chunk'ını ve bağlı kartlarının chunk'larını çalışır tutar (en fazla 9, configden). |
| **Bileşen Modülü** | Ayırıcı: modlu bileşenlerin içine gömülü eşyaları da çıkarır. |
| **Füzyon Modülü** | Ayırıcı: FUSE modunu açar. |
| **Lojistik Anahtarı** | Boru kolu: NORM / ÇEK / VER / KAPALI. Boru merkezi: redstone modu. Yansıtıcı yüzü: hedef. |

## Ekran görüntüleri

![omni_press_hero.png](press/omni_press_hero.png)
*Tüm test alanı: modun her bloğu ve dört başka mod, hepsi kartlarla besleniyor.*

![omni_press_pipes.png](press/omni_press_pipes.png)
*Hatlar, Envanter Yansıtıcı, Bileşen Ayırıcı ve Kablosuz Yönlendirici.*

![omni_press_hall.png](press/omni_press_hall.png)
*Tek Toplu Dağıtıcı, üç ayrı moddan dört makineyi besliyor.*

![omni_press_assembler.png](press/omni_press_assembler.png)
*KÜME modu: tek sandık, beş AE2 Molecular Assembler, her biri bir partiyi alıyor.*

![omni_press_mekanism.png](press/omni_press_mekanism.png)
*Aynı düzen beş Mekanism Enrichment Chamber ile - hiçbir yüz ayarı yapılmadan.*

![omni_press_mecraft.png](press/omni_press_mecraft.png)
*Kendi kendine craft eden AE2 Pattern Provider döngüsü: kimse hiçbir şeye tıklamıyor.*

![omni_press_ae2.png](press/omni_press_ae2.png)
*Gerçek bir ME şebekesi: borularımız eşyayı koyuyor ve alıyor, AE2 parçası gerekmiyor.*

![omni_press_empowerer.png](press/omni_press_empowerer.png)
*Actually Additions: dört stand, dört kartla besleniyor ve güçleniyor, kablo yok.*

![omni_press_miners.png](press/omni_press_miners.png)
*Void Madencileri, her kademeden biri, her birinde filtre olarak bir kart.*

![omni_press_monitor.png](press/omni_press_monitor.png)
*Makine Ekranı: bir kartı makineye bağla, odanın öbür ucundan oku.*

![omni_press_upgrade_1st.png](press/omni_press_upgrade_1st.png)
*Kart yükseltme töreni: bir elde kart, diğerinde altın, sağ tıkı basılı tut.*

![omni_gui_router.png](press/omni_gui_router.png)
*Kablosuz Yönlendirici: sekiz kart yuvası, iki yükseltme yuvası ve elle yazılan tik alanı.*

![omni_gui_card16.png](press/omni_gui_card16.png)
*Kart filtresi: 16 referans eşya, etiketler, büyüler, herhangi bir veri bileşeni.*

