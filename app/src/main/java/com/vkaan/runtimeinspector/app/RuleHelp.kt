package com.vkaan.runtimeinspector.app

/** Bir bulgunun neden çıktığı ve ne yapılırsa çıkmayacağı. Ekranda gösterilen metin. */
internal data class RuleHelp(val cause: String, val fix: String)

internal val RULE_HELP: Map<String, RuleHelp> = mapOf(
    "PREVIOUS_TRANSACTION_NOT_FINISHED" to RuleHelp(
        cause = "Kart servisi CONTINUE_EMV veya FULL_EMV durumundayken, yani açık bir EMV " +
            "işlemi completeEmvTxn bekliyorken, emvProcessType 1 ile yeni bir getCard geldi. " +
            "Önceki işlem hiç kapanmadı.",
        fix = "Yeni bir kart okuma başlatmadan önce açık işlemi bitir: completeEmvTxn çağır, " +
            "ya da iptal/hata durumunda akışı düzgün sonlandırıp state'i sıfırla. Kullanıcı " +
            "ekranı yarıda bırakıp baştan başlayabiliyorsa, o durumda da açık işlemi kapatan bir " +
            "çağrı olmalı.",
    ),
    "TRANSACTION_INTERRUPTED" to RuleHelp(
        cause = "Kart işlemi açıkken uygulama çöktü, arka plana düştü, ekran kapandı ya da " +
            "benzeri bir kesinti yaşandı. Terminal, kart okumayı bitiremeden yarıda kaldı.",
        fix = "İşlem ekranını kesintiye dayanıklı yap: onPause/onStop'ta açık işlemi iptal et " +
            "veya kaldığı yerden sürdürebileceğin bir state kaydet. Ödeme ekranında ekranın " +
            "kapanmasını engellemek (keepScreenOn) ve config değişimlerinde Activity'nin " +
            "yeniden yaratılmasına hazırlıklı olmak da bu uyarıyı ortadan kaldırır.",
    ),
    "CARD_REMOVED_DURING_TRANSACTION" to RuleHelp(
        cause = "EMV işlemi hâlâ açıkken kart çıkartıldı (IccTakeOut). Chip " +
            "gittiği için completeEmvTxn issuer cevabını karta uygulayamaz.",
        fix = "Kart okuma ekranında kullanıcıya kartı çıkarmamasını net söyle ve completeEmvTxn " +
            "dönene kadar ekranı kapatma. Kart erken çıktıysa işlemi iptal edip baştan başlat; " +
            "yarım kalmış EMV akışını sürdürmeye çalışma.",
    ),
    "CONTACTLESS_CONFIG_BEFORE_CONTACT_CONFIG" to RuleHelp(
        cause = "setEMVCLConfiguration, setEMVConfiguration'dan önce çağrıldı. Temassız " +
            "kernel'i ayarlanırken temaslı tarafın ayarı henüz yapılmamış — " +
            "platform bunu RemoteException ile de reddediyor.",
        fix = "Sırayı düzelt: önce setEMVConfiguration, sonra setEMVCLConfiguration. İkisini " +
            "aynı yerde, sırayla ve bind tamamlandıktan sonra çağır; ayrı ayrı yerlerden " +
            "tetiklenen konfigürasyon çağrıları bu sırayı bozuyor.",
    ),
    "ONLINE_PIN_AFTER_TRANSACTION_COMPLETE" to RuleHelp(
        cause = "completeEmvTxn'den sonra online PIN istendi. İşlem o noktada sonlanmış " +
            "durumda, dolayısıyla PIN'in gideceği bir yer yok.",
        fix = "Online PIN akışını completeEmvTxn'den önce tamamla. PIN gerekip gerekmediğini " +
            "kart cevabından okuyup kararı ondan sonra ver; tamamlama çağrısını akışın " +
            "en sonuna bırak.",
    ),
    "CARD_SERVICE_BOUND_TWICE" to RuleHelp(
        cause = "Kart servisine bir client zaten bağlıyken (unbound satırı gelmeden) yeniden " +
            "bind olundu. Normal akış bind → unbound → bind'dir; aradaki unbound atlandığı için " +
            "önceki bağlantı hiç bırakılmadı.",
        fix = "Her bind'i bir unbind ile eşle: bağlandığın yerde onDestroy/onStop'ta unbindService " +
            "çağır. Aynı servise birden fazla yerden (iki Activity/Fragment) bağlanıyorsan tek bir " +
            "yere indir; bırakılmayan bağlantı sızar ve callback'ler iki kez tetiklenebilir.",
    ),
    "CARD_SERVICE_CALLED_BEFORE_BIND" to RuleHelp(
        cause = "Kart servisi bind olduğunu bildirmeden önce bir API çağrısı yapıldı. Servis " +
            "bu çağrıları sessizce yok sayabilir ya da reddedebilir.",
        fix = "Çağrıları bind callback'inden sonra yap; bind'i beklemeden getCard/config " +
            "çağırma. Activity'nin onCreate'inde tetikleniyorsa, bağlantı hazır olduğunda " +
            "çalışacak bir kuyruğa al.",
    ),
    "CARD_SERVICE_CALLED_AFTER_ACTIVITY_STOPPED" to RuleHelp(
        cause = "Çağrıyı yapan Activity artık RESUMED değilken kart servisine bir çağrı " +
            "gitti. Servis, host duraklamışken gelen çağrıları reddediyor.",
        fix = "İşlem çağrılarını yalnızca ekran kullanıcının önündeyken (Activity aktifken) " +
            "yap. Arka planda dönen bir timer çağrıyı geciktirip onPause'dan sonra " +
            "gönderiyorsa, o işi lifecycle'a bağla ve durdur.",
    ),
    "FRAGMENT_ADDED_WHILE_STOPPED" to RuleHelp(
        cause = "Host Activity STOPPED durumundayken Fragment oluşturuldu. Bu, klasik " +
            "\"can not perform this action after onSaveInstanceState\" hatasının kaynağı: " +
            "ekranın durumu zaten kaydedilmişken üstüne yeni bir değişiklik yapılmaya çalışılıyor.",
        fix = "Fragment işlemlerini yalnızca Activity RESUMED iken yap. Asenkron bir cevap " +
            "sonrası ekran değiştiriyorsan, cevabı lifecycle-aware bir yerde topla " +
            "(ör. STARTED'a bağlı bir akış) veya işlemi ekran öne dönene kadar beklet.",
    ),
    "BACK_STACK_TOO_DEEP" to RuleHelp(
        cause = "Back stack derinliği limiti aştı. Aynı ekranlar üst üste yığılıyor, yani geri tuşu " +
            "kullanıcıyı beklediği yere götürmüyor ve bellek boşuna doluyor.",
        fix = "Aynı ekrana tekrar giderken yeni instance yaratmak yerine mevcut olanı kullan: " +
            "singleTop/launchSingleTop, ya da yeni ekrana geçerken tamamlanmış adımları " +
            "stack'ten çıkar (popBackStack / FLAG_ACTIVITY_CLEAR_TOP).",
    ),
    "DUPLICATE_SCREEN" to RuleHelp(
        cause = "Aynı ekranın birden fazla canlı kopyası var. Genelde çift tıklama ya da " +
            "aynı navigasyonun iki kez tetiklenmesi.",
        fix = "Navigasyonu tek tetiklemeye kilitle: butonu tıklandığında devre dışı bırak, " +
            "ya da açılışı singleTop/launchSingleTop ile aynı instance'a yönlendir.",
    ),
    "ORPHAN_FRAGMENT" to RuleHelp(
        cause = "Host Activity yok edildikten bir saniyeden fazla süre sonra Fragment hâlâ " +
            "canlı. Bir şey hâlâ ona referans tutuyor, yani ortada bir sızıntı var.",
        fix = "Fragment'in dışarıya verdiği referansları onDestroyView/onDestroy'da bırak: " +
            "listener'lar, callback'ler, statik alanlar, uzun ömürlü singleton'lara verilen " +
            "this. Handler ve arka planda başlattığın işleri de aynı yerde iptal et.",
    ),
    "ACTIVITY_LEAK" to RuleHelp(
        cause = "Activity yok edildikçe heap düşmek yerine yükseldi. Yok edilen Activity'ler " +
            "toplanamıyor, bir şey hâlâ onlara referans tutuyor.",
        fix = "Activity context'ini uzun ömürlü yerlere verme: singleton, statik alan, " +
            "application-scope listener. Kayıt yaptığın her şeyi (broadcast receiver, " +
            "observer, callback) onDestroy'da geri al.",
    ),
    "LOW_MEMORY_WHILE_FOREGROUND" to RuleHelp(
        cause = "Heap sınırına yaklaştı ya da sistem kritik seviyede bellek uyarısı verdi. " +
            "Bu noktadan sonra çöp toplayıcı (GC) sürekli çalışır ve OutOfMemoryError yakındır.",
        fix = "Büyük nesneleri küçült ya da erken bırak: bitmap'leri ekran boyutuna göre " +
            "yükle, listeleri sayfalı çek, önbellekleri sınırla. onTrimMemory geldiğinde " +
            "gerçekten bir şey serbest bırak.",
    ),
    "MID_FLOW_CRASH" to RuleHelp(
        cause = "Yakalanmamış bir exception uygulamayı düşürdü; üstelik bir akış açıkken " +
            "olduysa işlem yarıda kaldı.",
        fix = "Stack trace'i logcat'ten al ve kök nedeni düzelt. Ayrıca ödeme akışında " +
            "çökmenin yarım işlem bırakmaması için, açılışta yarım kalmış işlemi tespit " +
            "edip iptal eden bir kurtarma yolu bırak.",
    ),
    "ACTIVITY_RECREATED_MID_FLOW" to RuleHelp(
        cause = "Açık bir akış varken Activity bir konfigürasyon değişikliği için yok edildi " +
            "(dönme, dil, tema, ekran boyutu). Yeniden yaratılan ekran işlemin state'ini " +
            "kaybedebilir.",
        fix = "Akış state'ini Activity'nin dışına taşı (ViewModel veya saklanan state) ve " +
            "onSaveInstanceState ile geri yükle. Ödeme ekranı için ilgili konfigürasyon " +
            "değişimlerini configChanges ile kendin ele almak da bir seçenek.",
    ),
    "APP_BACKGROUNDED_MID_FLOW" to RuleHelp(
        cause = "Açık bir navigasyon akışı varken uygulama arka plana düştü. Kullanıcı " +
            "döndüğünde akış yarım halde bekliyor.",
        fix = "Arka plana düşerken akışı ya iptal et ya da kaldığı yerden sürdürülebilir " +
            "biçimde kaydet. Ödeme gibi zaman aşımı olan akışlarda geri dönüşte süreyi " +
            "kontrol et ve gerekiyorsa baştan başlat.",
    ),
    "SCREEN_OFF_MID_FLOW" to RuleHelp(
        cause = "Akış açıkken ekran kapandı. Çoğunlukla kullanıcı beklerken idle timeout " +
            "devreye girer ve işlem yarıda kalır.",
        fix = "İşlem ekranında ekranı açık tut (keepScreenOn) ve kendi zaman aşımını " +
            "yönet: süre dolduğunda işlemi kendin iptal edip kullanıcıya net bir sonuç " +
            "göster.",
    ),
    "NETWORK_LOSS" to RuleHelp(
        cause = "Bağlantı koptu; akış açıkken olduysa gönderilmiş ama cevabı gelmemiş istek " +
            "belirsiz durumda " +
            "kaldı — banka tarafında geçmiş ama cevabı alınmamış olabilir.",
        fix = "İstekleri idempotent yap ve yeniden denemede aynı referansı kullan, böylece " +
            "çift çekim olmaz. Cevapsız kalan işlem için sonucu sorgulayan bir " +
            "reconciliation adımı ekle.",
    ),
    "NETWORK_DROPPING_REPEATEDLY" to RuleHelp(
        cause = "Bağlantı kısa aralıklarla birçok kez koptu. Zayıf sinyal ya da sürekli " +
            "yeniden bağlanan bir network arayüzü; böyle bir bağlantıda tek denemeyle işlemi " +
            "garanti edemezsin.",
        fix = "Yeniden deneme politikasını artan bekleme süreleriyle (exponential backoff) kur " +
            "ve her denemede aynı " +
            "işlem referansını gönder. Kritik akışı başlatmadan önce bağlantının bir süre " +
            "stabil kaldığını kontrol et.",
    ),
    "POWER_LOSS_MID_FLOW" to RuleHelp(
        cause = "Akış açıkken cihaz kapanıyor ya da pil kritik seviyede. Terminal işlemin " +
            "ortasında ölebilir.",
        fix = "Pil düşükken yeni işlem başlatmayı engelle veya kullanıcıyı uyar. Açılışta " +
            "yarım kalmış işlemi tespit edip iptal eden bir kurtarma adımı bırak.",
    ),
)
