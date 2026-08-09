package tw.nekomimi.nekogram;

import java.util.HashMap;
import java.util.Locale;

/**
 * MeeroX v171 - every MeeroX feature string (names, descriptions,
 * dialogs...) relocated OUT of resources.arsc INTO the encrypted DEX
 * vault (this class ships only inside assets/meero_vault/dex.enc).
 *
 * Before v171 these 455 strings sat in res values dirs (values +
 * values-ar)/strings_meerox.xml, readable by any static-analysis /
 * unpack tool via the resource table. Now the resource dump shows
 * NOTHING about MeeroX features.
 *
 * Generated from strings_meerox.xml exactly (values preserved 1:1 with
 * android unescaping applied) - do not edit by hand, regenerate.
 */
public final class MeeroStrings {

    private MeeroStrings() {
    }

    /** key -> { english, arabic (nullable) } */
    private static final HashMap<String, String[]> MAP = new HashMap<String, String[]>();

    static {
        put("BackAnimationIos", "iOS", "آي أو إس");
        put("JanitorDays14", "14 days", "14 يوم");
        put("JanitorDays30", "30 days", "30 يوم");
        put("JanitorDays7", "7 days", "7 أيام");
        put("JanitorModeDaily", "Daily", "يومي");
        put("JanitorModeLimit", "Only when over the limit", "فقط عند تجاوز الحد");
        put("JanitorModeWeekly", "Weekly", "أسبوعي");
        put("MeeroBootLogDlgTitle", "Boot timeline", "خط زمن الإقلاع");
        put("MeeroBootLogEmpty", "No boot records on this device yet.", "لا توجد سجلات إقلاع على هذا الجهاز بعد.");
        put("MeeroBootLogTitle", "Boot log (support)", "سجل الإقلاع (للدعم)");
        put("JanitorReport", "Storage janitor freed %1$s of cache", "حارس التخزين حرّر %1$s من الكاش");
        put("MeeroAmoledBubblesInfo", "On dark themes with a pure-black background, incoming bubbles turn true #000000 black for contrast and battery saving. OFF restores the theme colors.", "مع الثيمات الداكنة ذات الخلفية السوداء الخالصة، فقاعات الطرف الثاني تصير سوداء 100% (#000000) للتباين وتوفير البطارية. الإطفاء يرجّع ألوان الثيم.");
        put("MeeroAmoledStrokeInfo", "Draws a barely-there 1px outline around pure-black incoming bubbles so their borders stay visible on true-black backgrounds. Works only while AMOLED black bubbles is on.", "يرسم خطاً خافتاً جداً بسماكة 1px حول فقاعات الطرف الثاني السوداء الخالصة حتى تبقى حدودها واضحة على الخلفية السوداء الحقيقية. يشتغل فقط إذا خيار «فقاعات سوداء AMOLED» شغّال.");
        put("MeeroAppEdition", "App edition", "إصدار التطبيق");
        put("MeeroAuditChat", "Locked chat", "محادثة مقفلة");
        put("MeeroAuditFailed", "Wrong code", "رمز خطأ");
        put("MeeroAuditSettings", "Lock settings", "إعدادات القفل");
        put("MeeroAuditSuccess", "Succeeded", "ناجحة");
        put("MeeroAuditVault", "Hidden vault", "القبو المخفي");
        put("MeeroAutoJanitorInfo", "Automatically cleans re-downloadable cached media on the schedule below. Messages and the database are never touched; music files are kept.", "ينظّف الكاش القابل لإعادة التحميل تلقائياً حسب الجدولة أدناه. لا يمس الرسائل ولا قاعدة البيانات أبداً، وملفات الموسيقى مستثناة.");
        put("MeeroAutoRelock", "Auto relock when leaving the app", "القفل التلقائي عند الخروج من التطبيق");
        put("MeeroAutoReplyBounds", "Guarantees: private chats only - groups, channels, bots and your Saved Messages are always excluded. Never replies while you are inside that same chat with the screen on. Works in the background after you leave the app (a device \"Force stop\" kills everything - that is Android law). With Ghost Mode: the reply is sent without any read receipt, so the other side's read ticks stay unchanged. The cooldown is kept in memory and resets when the app restarts.", "الضمانات: الدردشات الخاصة فقط — القروبات والقنوات والبوتات ورسائلك المحفوظة مستبعدة دائماً. لا يرد وأنت داخل نفس الدردشة والشاشة شغالة. يعمل في الخلفية ولو خرجت من التطبيق («الإيقاف الإجباري» من إعدادات الجهاز يطفي كل شيء — قانون أندرويد). مع وضع الشبح: الرد يُرسل بدون إيصال قراءة، فصحات القراءة عند الطرف الثاني لا تتغير. الفاصل الزمني يُحفظ بالذاكرة ويُصفَّر عند إعادة تشغيل التطبيق.");
        put("MeeroAutoReplyCooldown", "Reply cooldown per chat", "الفاصل بين الردود لنفس الدردشة");
        put("MeeroAutoReplyDefaultText", "Hi {name} 👋 I'm busy right now, I'll get back to you as soon as I can ✅", "أهلاً {name} 👋 مشغول هسة، أرد عليك بأقرب وقت إن شاء الله ✅");
        put("MeeroAutoReplyDelay", "Delay before sending", "التأخير قبل الإرسال");
        put("MeeroAutoReplyInfo", "Automatically sends one reply to incoming messages in private chats while you are busy, with a per-chat cooldown so it never becomes spam. Off by default - and while off, nothing is ever sent, exactly the stock behavior.", "يرد تلقائياً برسالة واحدة على الرسائل الجديدة في الدردشات الخاصة وأنت مشغول، مع فاصل زمني لكل دردشة حتى لا يتحول لسبام. مطفي افتراضياً — وعند إطفائه لا يُرسل أي شيء، سلوك رسمي تماماً.");
        put("MeeroAutoReplySampleName", "Ali", "علي");
        put("MeeroAutoReplyText", "Reply text", "نص الرد");
        put("MeeroAutoReplyTextHint", "Write {name} and it becomes the person's name", "اكتب {name} وتنحط اسم الشخص مكانها");
        put("MeeroAutoReplyTitle", "Auto-reply", "الرد التلقائي");
        put("MeeroAutoReplyUsage", "How it works: automatically sends one reply to incoming messages in private chats while you are busy, with a per-chat cooldown so it never becomes spam. Off by default - and while off, nothing is ever sent, exactly the stock behavior.\n\n⏰ Time window: when enabled, replies are only sent within these hours on the chosen days. If the start is later than the end, the window crosses midnight (e.g. 23:00-08:00). Outside the window (or on unchecked days) nothing is sent. Off = replies around the clock, exactly like before. The optional night text replaces the general reply text while the window is active.\n\n✅ Guarantees: private chats only - groups, channels, bots and your Saved Messages are always excluded. Never replies while you are inside that same chat with the screen on. Works in the background after you leave the app (a device \"Force stop\" kills everything - that is Android law). With Ghost Mode: the reply is sent without any read receipt, so the other side's read ticks stay unchanged. The cooldown is kept in memory and resets when the app restarts.", "شلون تشتغل: يرد تلقائيًا برسالة واحدة على الرسائل الجديدة في الدردشات الخاصة وأنت مشغول، مع فاصل زمني لكل دردشة حتى لا يتحول لسبام. مطفي افتراضيًا — وعند إطفائه ما ينرسل أي شيء، سلوك رسمي تمامًا.\n\n⏰ النافذة الزمنية: عند التفعيل يُرسل الرد فقط داخل هاي الساعات وبالأيام المختارة. إذا البداية بعد النهاية فالنافذة تعبر منتصف الليل (مثال 23:00–08:00). خارج النافذة أو بيوم غير محدد ما ينرسل رد. مطفية = يرد على مدار الساعة مثل السابق. «النص الليلي» الاختياري يستبدل نص الرد العادي داخل النافذة فقط.\n\n✅ الضمانات: الدردشات الخاصة فقط — القروبات والقنوات والبوتات ورسائلك المحفوظة مستبعدة دائمًا. لا يرد وأنت داخل نفس الدردشة والشاشة شغالة. يعمل بالخلفية ولو خرجت من التطبيق («الإيقاف الإجباري» من إعدادات الجهاز يطفي كل شيء — قانون أندرويد). مع وضع الشبح: الرد ينرسل بدون إيصال قراءة، فصحات القراءة عند الطرف الثاني ما تتغير. الفاصل الزمني ينحفظ بالذاكرة وينصفّر عند إعادة تشغيل التطبيق.");
        put("MeeroAutoReplyWindowDays", "Window days", "أيام النافذة");
        put("MeeroAutoReplyWindowDaysAll", "Every day", "كل الأيام");
        put("MeeroAutoReplyWindowDaysNone", "No days - window never runs", "بدون أيام (النافذة لا تعمل)");
        put("MeeroAutoReplyWindowEnd", "Window end", "نهاية النافذة");
        put("MeeroAutoReplyWindowInfo", "When enabled, replies are only sent within these hours on the chosen days. If the start is later than the end, the window crosses midnight (e.g. 23:00-08:00). Outside the window (or on unchecked days) nothing is sent. Off = replies around the clock, exactly like before. The optional night text replaces the general reply text while the window is active.", "عند التفعيل يُرسل الرد فقط داخل هذه الساعات وفي الأيام المختارة. إذا كانت البداية بعد النهاية فالنافذة تعبر منتصف الليل (مثال 23:00–08:00). خارج النافذة (أو بيوم غير محدد) لا يُرسل أي رد. مطفية = يرد على مدار الساعة مثل السابق تماماً. «النص الليلي» الاختياري يستبدل نص الرد العادي داخل النافذة فقط.");
        put("MeeroAutoReplyWindowStart", "Window start", "بداية النافذة");
        put("MeeroAutoReplyWindowTitle", "Auto-reply time window 🌙", "نافذة زمنية للرد 🌙");
        put("MeeroBubbleStyle", "Chat bubbles", "فقاعات المحادثة");
        put("MeeroCardsInfo", "Group settings rows into inset rounded cards, iOS 26 style.", "تجميع صفوف الإعدادات ببطاقات مدوّرة بأسلوب iOS 26.");
        put("MeeroChannel1", "Channel 1", "القناة الأولى");
        put("MeeroChannel2", "Channel 2", "القناة الثانية");
        put("MeeroChatLockAdd", "Lock a chat", "قفل دردشة");
        put("MeeroChatLockChangeCode", "Change code", "تغيير الرمز");
        put("MeeroChatLockCodeMismatch", "The two codes don't match - try again", "الرمزان مو متطابقين - حاول مرة ثانية");
        put("MeeroChatLockCodeSaved", "Code saved", "انحفظ الرمز");
        put("MeeroChatLockCodeWrong", "Wrong code - try again", "الرمز غلط - حاول مرة ثانية");
        put("MeeroChatLockConfirmCode", "Confirm the code", "أكّد الرمز");
        put("MeeroChatLockEmpty", "No locked chats yet. Pick one above.", "ماكو دردشات مقفلة بعد. اختار وحدة من فوق.");
        put("MeeroChatLockEnterCode", "Enter code", "أدخل الرمز");
        put("MeeroChatLockEnterCodeHint", "Enter your 8-digit lock code", "اكتب رمز القفل المكوّن من 8 أرقام");
        put("MeeroChatLockGateHint", "Unlock to view this conversation", "افتحها حتى تشوف المحادثة");
        put("MeeroChatLockGateSubtitle", "Unlock with your fingerprint or device lock", "افتحها ببصمتك أو رمز جهازك");
        put("MeeroChatLockGateTitle", "Locked chat", "محادثة مقفلة");
        put("MeeroChatLockHeader", "Locked chats", "الدردشات المقفلة");
        put("MeeroChatLockInfo", "A locked chat is covered by an opaque gate and asks for your unlock secret on EVERY entry path (list, search, notification, link) - system biometric/device lock or your 8-digit code, chosen above. The chat also disappears from the main list, folders, archive and every search result, and reappears only inside Hidden chats (long-press the Chats tab on the bottom bar). It relocks the moment you leave it (screen rotation relocks too). Locking also mutes the chat server-side so system notifications reveal nothing, and you get a private \"new message in a locked chat\" notice instead. Unlocking restores notifications only if the lock muted them. The 8-digit code is stored on this device ONLY as a salted hash - if you forget it there is NO recovery (you would have to clear the app data, which logs you out). Off = everything behaves stock.", "الدردشة المقفلة تنغطى بستارة معتمة وتطلب سر الفتح بكل طرق الدخول (القائمة، البحث، الإشعار، الرابط) - بصمة/قفل الجهاز أو رمز الـ8 أرقام حسب ما تختار من فوق. الدردشة أيضاً تختفي من القائمة الرئيسية والمجلدات والأرشيف وكل نتائج البحث، وترجع تظهر بس بشاشة «المحادثات المخفية» (ضغطة مطولة على تبويب المحادثات بالشريط السفلي). تنقفل لحظة خروجك منها (تدوير الشاشة ينقفل أيضاً). القفل يكتم إشعارات الدردشة من السيرفر حتى ما ينكشف شي بإشعارات النظام، ويوصلك بدلها إشعار خاص «رسالة جديدة بمحادثة مقفلة». فك القفل يرجّع الإشعارات بس إذا القفل هو اللي كتمها. رمز الـ8 أرقام ينحفظ بهذا الجهاز فقط كتجزئة مملّحة - إذا تنساه ماكو أي طريقة استرجاع (لازم تمسح بيانات التطبيق ويعني تسجل دخول من جديد). مطفي = كل شي يرجع أصلي.");
        put("MeeroChatLockInvalid", "This chat cannot be locked.", "هاي الدردشة ما ينقفل عليها.");
        put("MeeroChatLockMaster", "Enable chat lock", "تفعيل قفل المحادثات");
        put("MeeroChatLockMethod", "Unlock method", "طريقة فتح القفل");
        put("MeeroChatLockMethodCode", "8-digit code", "رمز من 8 أرقام");
        put("MeeroChatLockMethodSystem", "Device biometric / device lock", "بصمة / قفل الجهاز");
        put("MeeroChatLockNewMessage", "New message in a locked chat", "وصلتك رسالة جديدة بمحادثة مقفلة");
        put("MeeroChatLockRemove", "Remove lock", "فك القفل");
        put("MeeroChatLockRemoveConfirm", "Unlock %1$s and restore its notifications?", "تفك القفل عن %1$s وترجّع إشعاراتها؟");
        put("MeeroChatLockRowDetail", "Locked - fingerprint needed on entry", "مقفلة - البصمة مطلوبة عند الدخول");
        put("MeeroChatLockSetCode", "Set your code", "عيّن الرمز");
        put("MeeroChatLockSetCodeHint", "Enter an 8-digit code (numbers only)", "اكتب رمز مكوّن من 8 أرقام (أرقام فقط)");
        put("MeeroChatLockTitle", "Chat lock", "قفل المحادثات");
        put("MeeroChatsMenuFogInfo", "Blurs the whole screen behind the bottom-bar chats long-press popup so the menu stays in focus. Off = plain dimming, exactly like before.", "يضبّب الشاشة كلها خلف قائمة الضغط المطول على زر المحادثات بالشريط السفلي حتى التركيز يبقى على القائمة. مطفي = تعتيم عادي مثل قبل بالضبط.");
        put("MeeroCooldown10", "10 minutes", "10 دقائق");
        put("MeeroCooldown30", "30 minutes", "30 دقيقة");
        put("MeeroCooldown5", "5 minutes", "5 دقائق");
        put("MeeroCooldown60", "1 hour", "ساعة واحدة");
        put("MeeroCooldownEveryMessage", "Every message", "كل رسالة");
        put("MeeroDayFri", "Friday", "الجمعة");
        put("MeeroDayMon", "Monday", "الاثنين");
        put("MeeroDaySat", "Saturday", "السبت");
        put("MeeroDaySun", "Sunday", "الأحد");
        put("MeeroDayThu", "Thursday", "الخميس");
        put("MeeroDayTue", "Tuesday", "الثلاثاء");
        put("MeeroDayWed", "Wednesday", "الأربعاء");
        put("MeeroDelay10", "10 seconds", "10 ثواني");
        put("MeeroDelay3", "3 seconds", "3 ثواني");
        put("MeeroDelay5", "5 seconds", "5 ثواني");
        put("MeeroDelayInstant", "Instant", "فوري");
        put("MeeroDeveloper", "Developer", "المطوّر");
        put("MeeroDialogsStyleInfo", "Centred title, filled folder tabs, pill search field and an always-visible Edit button. Restart to apply everywhere.", "عنوان بالنص، تبويبات ممتلئة، حقل بحث مدوّر، وزر تحرير ظاهر دائماً. أعد التشغيل للتطبيق الكامل.");
        put("MeeroExclusionsAdd", "Add exclusion", "إضافة استثناء");
        put("MeeroExclusionsInfo", "People here never receive your auto-reply at all - even if they have their own text rule. Remove a person and replies work for him again as usual.", "الأشخاص هنا لا يوصلهم ردك التلقائي أبداً — حتى لو إلهم قاعدة نص خاصة. إذا شلت الشخص يرجع الرد يشتغل عليه طبيعي.");
        put("MeeroExclusionsNone", "No exclusions", "بلا استثناءات");
        put("MeeroExclusionsRemove", "Remove from exclusions", "إزالة من الاستثناءات");
        put("MeeroExclusionsRowDetail", "Never receives the auto-reply", "لن يصله أي رد تلقائي");
        put("MeeroExclusionsTitle", "Excluded people", "استثناء الأشخاص");
        put("MeeroExclusionsWordMany", "excluded", "مستثنى");
        put("MeeroExclusionsWordOne", "excluded", "مستثنى");
        put("MeeroFlexWidthInfo", "Short iOS-style menus shrink toward their content instead of the fixed 252dp minimum. Long menus stay exactly as they are.", "القوائم القصيرة بنمط iOS تصغّر نحو محتواها بدل الحد الأدنى الثابت 252dp. القوائم الطويلة تبقى كما هي تماماً.");
        put("MeeroFontAdd", "Add a font from your device", "إضافة خط من جهازك");
        put("MeeroFontBadFormat", "That file could not be loaded as a font. Pick a valid .ttf or .otf file.", "تعذّر تحميل هذا الملف كخط. اختر ملف .ttf أو .otf صالح.");
        put("MeeroFontDeleteConfirm", "Delete this font?", "حذف هذا الخط؟");
        put("MeeroFontPick", "Select a .ttf or .otf font", "اختر ملف خط .ttf أو .otf");
        put("MeeroFontSection", "Fonts", "الخطوط");
        put("MeeroGateCodeHint", "Enter your lock code to open", "اكتب رمز القفل حتى تفتح");
        put("MeeroGhostSwipeReadInfo", "\"Don't read messages\" hides read receipts from everyone, but sometimes you want one person to know you read their chat: enable this, swipe that chat in the dialogs list, and its receipt is sent alone while everything else stays hidden. One requirement: the swipe action must be Read - set it in the Chat swipe action row above, or in Settings - Chat Settings - Chat list swipe gesture. When off, ghost mode behaves exactly like the stock fork.", "خيار «عدم قراءة الرسائل» يحجب إيصالات القراءة عن الكل، لكن أحياناً تريد أن يعرف شخصٌ واحد أنك قرأت: فعّل هذا الخيار واسحب تلك المحادثة في قائمة الدردشات فيصلها وحدها إيصال قراءة حقيقي، والباقي يبقى مخفياً. شرط واحد: إجراء السحب لازم يكون «مقروءة» — تضبطه من صف «ضبط إجراء السحب» في الأعلى، أو من: الإعدادات ← إعدادات المحادثات ← «إجراءات السحْب لقائمة المحادثات». عند الإطفاء يرجع الشبح لسلوك الفورك الرسمي بالضبط.");
        put("MeeroGhostSwipeReadNeedRead", "For ghost swipe-read to work, the swipe action must be Read - set it in the Chat swipe action row above.", "لتعمل القراءة الشبحية بالسحب لازم إجراء السحب يكون «مقروءة» — اضبطه من صف «ضبط إجراء السحب» بالأعلى.");
        put("MeeroGlassBordersInfo", "A hairline outline on menus, panels and the search field.", "خط رفيع حول القوائم واللوحات وحقل البحث.");
        put("MeeroGlassSettingsInfo", "A fixed exclusive skin for Meero settings: frosted glass with a soft glow. It ignores Telegram themes completely and only follows day/night mode.", "جلد ثابت حصري لإعدادات ميرو: زجاج شفاف بتوهّج هادئ. لا يتأثر بثيمات تيليجرام نهائياً ويتبدّل مع الوضع الليلي/النهاري فقط.");
        put("MeeroGlassSwitchesInfo", "Switches drawn exactly like the preview mock: a 48x28 pill that fills with a rose-to-violet gradient and a soft rose glow when on, a white knob that stretches under your finger, and 280ms springy motion. Off keeps the stock switch.", "سويتشات مطابقة لموقع المعاينة: حبة 48×28 تمتلئ بتدرّج وردي إلى بنفسجي مع توهّج وردي ناعم عند التشغيل، وكرة بيضاء تتمدد تحت إصبعك، وحركة نابضة 280ms. الإطفاء يرجّع السويتش الأصلي حرفياً.");
        put("MeeroGroupAppearance", "Appearance", "المظهر");
        put("MeeroGroupChat", "Chat", "المحادثة");
        put("MeeroGroupMotion", "Motion & feedback", "الحركة والإحساس");
        put("MeeroGroupNavigation", "Navigation", "التنقّل");
        put("MeeroGroupSound", "Sound & launch", "الصوت والبداية");
        put("MeeroGroupStorage", "Storage", "التخزين");
        put("MeeroHeaderEdit", "Edit", "تحرير");
        put("MeeroHeroMsg1", "Hey 👋 how are you?", "مرحبا 👋 شلونك؟");
        put("MeeroHeroMsg2", "Did you try the new Meero design?", "هلا والله! جرّبت تصميم ميرو الجديد؟");
        put("MeeroHeroMsg3", "Wooow fire 🔥", "وااو ناريي 🔥");
        put("MeeroHiddenChats", "Hidden chats", "المحادثات المخفية");
        put("MeeroHunterClearConfirm", "Delete the whole log?", "تمسح السجل كله؟");
        put("MeeroHunterDeleteConfirm", "Deletes %1$d selected items from the catcher log on this device only. Your chats are not affected.", "راح ينحذف %1$d عنصر محدد من سجل الصائد على جهازك فقط. محادثاتك ما تتأثر.");
        put("MeeroHunterDeletedMsg", "deleted a message", "حذف رسالة");
        put("MeeroHunterEditedMsg", "edited a message", "عدّل رسالة");
        put("MeeroHunterEmpty", "Nothing caught yet", "لا شيء ملتقط بعد");
        put("MeeroHunterInfo", "When the other side deletes or edits a message you get an instant notification and the event lands here with the original text (old ← new for edits) and the time. Own messages and service messages are never captured. Everything stays on your device. Switch off = nothing is caught and nothing is notified. Note: edit catching follows the built-in \"save edit history\" toggle (on by default). Long-press any entry to select one or more items and delete them.", "لما الطرف الثاني يحذف أو يعدّل رسالة يوصلك إشعار فوري وينسجل هنا النص الأصلي (قديم ← جديد بالتعديل) مع الوقت بالدقيقة. رسائلك أنت والرسائل الخدمية لا تُلتقط أبداً. كل شيء يبقى على جهازك. المفتاح مطفي = لا التقاط ولا إشعارات. ملاحظة: التقاط التعديل يتبع مفتاح «حفظ تاريخ التعديلات» المدمج (فعّال افتراضياً). اضغط مطولاً على أي عنصر لتحديد عنصر أو أكثر وحذفه.");
        put("MeeroHunterLogHeader", "Delete & edit log", "سجل الحذف والتعديل");
        put("MeeroHunterMaster", "Catch deletes & edits", "التقاط الحذف والتعديل");
        put("MeeroHunterMedia", "Media 📎", "وسائط 📎");
        put("MeeroHunterNothingSelected", "Nothing selected. Long-press an entry, tap the ones you want, then delete.", "ماكو عناصر محددة. اضغط مطولاً على عنصر، حدد اللي تريده، بعدين احذف.");
        put("MeeroHunterSelectAll", "Select all", "تحديد الكل");
        put("MeeroHunterSelectedCount", "Selected: %1$d", "المحدد: %1$d");
        put("MeeroHunterSomeone", "Someone", "شخص ما");
        put("MeeroHunterTitle", "Delete catcher", "صائد الحاذف");
        put("MeeroIconAlt", "MeeroX (alt)", "MeeroX (بديل)");
        put("MeeroIconStyle2", "MeeroX (style 2)", "MeeroX (نمط ٢)");
        put("MeeroIosAlertsInfo", "Rules a hairline above the buttons of a dialog and between each pair, the way Telegram for iOS separates its alert actions.", "يرسم خطاً رفيعاً فوق أزرار الحوار وبين كل زرين، مثل ما يفصل تلكرام آيفون أزرار تنبيهاته.");
        put("MeeroIosAnimInfo", "New messages spring into place from the corner, the way iOS does.", "الرسائل الجديدة تنبثق من الزاوية بحركة نابضة، مثل الآيفون.");
        put("MeeroIosCallInfo", "Draws the round controls on the call screen at the 72dp Telegram for iOS uses instead of 52, spaces them the way it does, and sets each label at 13dp, 8dp below its button.", "يرسم أزرار المكالمة الدائرية بقياس 72 مثل تلكرام آيفون بدل 52، ويباعد بينها بنفس طريقته، ويكتب كل تسمية بقياس 13 على بعد 8 تحت زرها.");
        put("MeeroIosCodeInfo", "Taller, softly rounded code boxes set in a monospaced face, sized the way Telegram for iOS draws them.", "خانات أطول وزواياها أنعم بخط ثابت العرض، بنفس قياسات تلكرام آيفون.");
        put("MeeroIosFastScrollInfo", "Lists every section letter down the edge so you can tap straight to one, instead of dragging a handle. Long indexes are thinned to fit.", "يعرض كل أحرف الأقسام على الحافة حتى تضغط الحرف وتنتقل له مباشرة، بدل سحب المقبض. القوائم الطويلة تُختصر لتناسب الشاشة.");
        put("MeeroIosHapticsInfo", "Distinct feedback for selections, confirmations and errors instead of one buzz for everything. Needs Android 11 or newer for the full set.", "اهتزاز مختلف للتنقّل والتأكيد والخطأ بدل اهتزاز واحد لكل شي. الشدّات الكاملة تحتاج أندرويد 11 أو أحدث.");
        put("MeeroIosIconsInfo", "Use Telegram for iOS's own glyphs. They are static, so the animated tab icons are replaced.", "استخدام أيقونات تلكرام آيفون الأصلية. هي ثابتة، فتُستبدل أيقونات التبويبات المتحركة.");
        put("MeeroIosInputPillInfo", "A floating, panel-free composer: attach moves into its own glass circle at the far left, the text sits in a capsule with the emoji inside it, and the mic becomes a neutral circle that turns into a rose send button while typing. Off restores the stock Telegram bar.", "لوحة شفافة فوق خلفية الدردشة: المشبك بدائرة زجاجية أقصى اليسار، كبسولة حقل بداخلها الإيموجي، والمايك دائرة رمادية تتحول لزر إرسال وردي عند الكتابة. إطفاءه يرجع تصميم تليكرام الرسمي.");
        put("MeeroIosIntroInfo", "iOS typography and button sizing on the intro pages, with page dots that slide instead of stretching. The animations themselves stay as they are.", "خطوط وأزرار بقياسات آيفون بصفحات الترحيب، ونقاط تنقّل تنزلق بدل ما تتمدّد. الرسوم المتحركة تظل مثل ما هي.");
        put("MeeroIosLoadingInfo", "Thinner ring that stretches as it spins, sized the way Telegram for iOS draws it.", "حلقة أرفع تتمدّد وهي تدور، بنفس قياسات تلكرام آيفون.");
        put("MeeroIosMainMenuInfo", "The main-screen menu styled like iOS: rounded card, iOS icons, hairline separators and no gap bars. OFF restores the stock menu exactly.", "قائمة الشاشة الرئيسية بشكل الآيفون: بطاقة مدورة، أيقونات بنمط iOS، فواصل شعرية بدون القضبان السميكة. الإطفاء يرجّع القائمة الأصلية حرفياً.");
        put("MeeroIosMediaGridInfo", "Prints a video's length straight onto its thumbnail under a soft shadow, as Telegram for iOS does, instead of boxing it in a filled tag.", "يكتب مدة الفيديو على الصورة مباشرة بظل خفيف مثل تلكرام آيفون، بدل حصرها داخل مستطيل ملوّن.");
        put("MeeroIosMenuAnimInfo", "A context menu scales up out of the message or row it belongs to, instead of always unfolding from its own corner.", "قائمة السياق تتوسّع من الرسالة أو الصف الي تخصّه، بدل ما تنفتح دائماً من زاويتها.");
        put("MeeroIosMsgMenuInfo", "The long-press message menu styled like iOS: same card, trailing icons and hairlines, with delete actions in their own red card.", "قائمة الضغطة المطولة على الرسالة بشكل الآيفون: نفس البطاقة والفواصل والأيقونات بالطرف المقابل، والحذف ببطاقة حمراء منفصلة.");
        put("MeeroIosPopupMenuInfo", "The ⋮ menu styled like iOS: rounded card, icons on the trailing edge, hairline separators, and destructive actions split into their own red card.", "قائمة ⋮ بشكل الآيفون: بطاقة مدوّرة، الأيقونة بالطرف المقابل، فواصل شعرية، وخيارات الحذف ببطاقة حمراء منفصلة.");
        put("MeeroIosRowInfo", "Lays a chat row out the way iOS does - a 60dp picture 16dp from the edge with the text starting at 84dp - and applies the same sizing to the account switcher, where it also shows each account's unread count.", "يرتّب صف المحادثة مثل الآيفون — صورة 60 على بعد 16 من الحافة والنص يبدأ عند 84 — ويطبّق نفس القياس على شاشة الحسابات، وهناك يعرض عدد غير المقروء لكل حساب.");
        put("MeeroIosSearchInfo", "Solid tinted pill with a smaller, dimmer magnifier, the way UISearchBar is drawn. Removes the hairline edge.", "كبسولة ممتلئة بعدسة أصغر وأخفت، مثل ما يرسمها آيفون. تشيل الحد الرفيع.");
        put("MeeroIosSelectionInfo", "Uses Telegram for iOS's own glyphs for the pin, mute, archive and delete controls shown while chats are selected, and for the entries in the overflow menu behind them.", "يستعمل رموز تلكرام آيفون نفسها لأزرار التثبيت والكتم والأرشفة والحذف الي تظهر عند تحديد محادثات، ولعناصر قائمة المزيد وراها.");
        put("MeeroIosShadowsInfo", "A wide, faint shadow under settings and profile cards, the way iOS suggests depth instead of drawing an outline.", "ظل واسع وخفيف تحت بطاقات الإعدادات والملف الشخصي، مثل ما يوحي آيفون بالعمق بدل رسم حد.");
        put("MeeroIosSoundsInfo", "Use Telegram for iOS's own send and receive tones.", "استخدام نغمات الإرسال والاستقبال الأصلية من تلكرام آيفون.");
        put("MeeroIosStoriesInfo", "Groups the collapsed story circles beside the \"Chats\" title and centres the pair the way iOS does, instead of parking them at the left edge, and draws them at the reference size.", "يجمّع دوائر الستوريات المطوية جنب عنوان «المحادثات» ويوسّط الاثنين معاً مثل الآيفون بدل تركها على الحافة اليسرى، ويرسمها بالحجم المرجعي.");
        put("MeeroIosWaveformInfo", "Wider, fully rounded waveform bars on a 4pt pitch, and a lighter recording indicator, matching Telegram for iOS.", "أعمدة أعرض ومدوّرة بالكامل بتباعد 4، ومؤشر تسجيل أخف، مثل تلكرام آيفون.");
        put("MeeroKeywordAdd", "Add keyword set", "إضافة طقم كلمات");
        put("MeeroKeywordAddAll", "All chats", "كل المحادثات");
        put("MeeroKeywordAddChat", "Specific chat", "دردشة محددة");
        put("MeeroKeywordAll", "All chats", "كل المحادثات");
        put("MeeroKeywordEdit", "Edit words", "تعديل الكلمات");
        put("MeeroKeywordEmpty", "No keyword sets yet. Add your first one.", "ماكو طقمات كلمات بعد. أضف أول وحدة.");
        put("MeeroKeywordHeader", "Keyword sets", "طقمات الكلمات");
        put("MeeroKeywordInfo", "You get an instant notification whenever a new incoming message contains one of your words - even from muted groups. It works while the app is alive in the background. Words shorter than 2 letters are ignored and each chat alerts at most once every 30 seconds. All matching happens on your device; nothing is sent anywhere. Off = nothing is watched.", "يوصلك إشعار فوري لما أي رسالة جديدة واردة تحتوي كلمة من كلماتك، حتى لو القروب مكتوم. يشتغل والتطبيق حي بالخلفية. الكلمة الأقصر من حرفين تُتجاهل، وكل دردشة تنبّه مرة وحدة كل ٣٠ ثانية. كل المطابقة تصير على جهازك وما ينرسل أي شي لأي مكان. مطفي = لا توجد أي مراقبة.");
        put("MeeroKeywordMaster", "Enable keyword alert", "تفعيل منبه الكلمات");
        put("MeeroKeywordTitle", "Keyword alert", "منبه الكلمات");
        put("MeeroKeywordWordsHint", "Comma separated, e.g. money, meeting", "كلمات مفصولة بفاصلة، مثال: فلوس, اجتماع");
        put("MeeroLockAudit", "Unlock attempt log", "سجل محاولات الفتح");
        put("MeeroLockAuditClear", "Clear the log", "مسح السجل");
        put("MeeroLockAuditClearConfirm", "Every recorded unlock attempt will be deleted. This cannot be undone.", "راح تنحذف كل المحاولات المسجلة، وما تكدر ترجعها بعد.");
        put("MeeroLockAuditCleared", "Log cleared", "انمسح السجل");
        put("MeeroLockAuditEmpty", "No attempts recorded yet", "لا توجد محاولات مسجلة بعد");
        put("MeeroLockAuditInfo", "Successful unlocks and wrong-code attempts are recorded for locked chats, the hidden vault and this section. Biometric unlocks are logged on success only. Everything stays on this device, newest first, last 40 entries.", "يسجّل محاولات الفتح الناجحة والرموز الخاطئة للمحادثات المقفلة والقبو المخفي وهاي الإعدادات. فتح البصمة يسجّل بس عند النجاح. كل شي يبقى بجهازك، الأحدث أول، آخر ٤٠ محاولة.");
        put("MeeroMenuBlurInfo", "Frosted glass backdrop when you long-press a message.", "خلفية زجاجية ضبابية عند الضغط المطوّل على رسالة.");
        put("MeeroNightText", "Night text", "النص الليلي");
        put("MeeroNightTextEmpty", "Not set - the general text is sent", "غير مضبوط (ينرسل النص العادي)");
        put("MeeroNightTextHint", "e.g. Sleeping now, will reply in the morning", "مثال: نايم هسه، أرد عليك باجر");
        put("MeeroNightTextOn", "Night reply text", "نص الرد الليلي");
        put("MeeroOnceConsentAccept", "I agree", "موافق");
        put("MeeroOnceConsentDecline", "Decline", "رفض");
        put("MeeroOnceConsentText", "Notice and release of liability (legal and religious): this feature (view-once guard) can save photos and videos others send as view-once, which may violate the sender's privacy, the terms of use, or even the law in your country. By pressing \"I agree\" you affirm before God and His people that you alone are civilly, criminally and religiously responsible for any use of this feature, that MeeroX and its developers carry no liability or claim whatsoever for your usage, and that you will only use it for a legitimate need. Not okay with that? Press \"Decline\" and leave. Agreement is saved once; every declined visit asks again.", "تنبيه وإبراء ذمة (قانونيًا ودينيًا): هاي الميزة (حارس مرة واحدة) تنحفظ منها الصور والفيديوهات اللي يرسلها غيرك بصيغة «عرض مرة واحدة»، وهاي الفعلة ممكن تخالف خصوصية المرسل وشروط الاستخدام وحتى قانون بلدك. بضغطك على «موافق» أنت تؤكد وتتعهد قدام الله وعباده: إنك المسؤول الوحيد مدنيًا وجنائيًا وشرعًا عن أي استخدام لهاي الميزة، وإن MeeroX ومطوريها ما يتحملون أي تبعة أو مطالبة أو مساءلة ناتجة عن استخدامك لها، وإنك رح تستخدمها بس للحاجة المشروعة. إذا ما موافق اضغط «رفض» وطلع. الموافقة تنحفظ مرة وحدة، وأي دخول بدونها يعيد الشاشة.");
        put("MeeroOnceConsentTitle", "One more thing first", "قبل ما نكمل");
        put("MeeroOnceCount", "Saved to gallery", "المحفوظات بالمعرض");
        put("MeeroOnceCountHeader", "Statistics", "إحصائيات");
        put("MeeroOnceInfo", "While ON, the context menu of a self-destruct / view-once photo or video shows its \"burn\" and \"save to gallery\" buttons - the app built-in saver stores your pick in the gallery and this counter plus a small notification confirm it. While OFF, once media behaves exactly like official Telegram: no save button at all. Nothing is downloaded automatically and nothing leaves your device.", "والميزة شغالة، قائمة خيارات وسائط عرض-مرة (الصور والفيديوهات المؤقتة) تظهر بيها زرّي «حرق» و«حفظ في المعرض» - الحفظ يستخدم أداة الحفظ المدمجة بالتطبيق ويأكده بإشعار صغير والعدّاد اللي فوق. ولما تكون مطفية تتصرف وسائط عرض-مرة تماماً مثل تلكرام الرسمي: بدون أي زر حفظ. ما ينحمّل أي شي تلقائياً وكل شي يبقى على جهازك.");
        put("MeeroOnceKindPhoto", "Photo", "صورة");
        put("MeeroOnceKindVideo", "Video", "فيديو");
        put("MeeroOnceMaster", "Save view-once media", "حفظ وسائط عرض مرة واحدة");
        put("MeeroOnceSavedNotif", "Saved to gallery", "انحفظت بالمعرض");
        put("MeeroOnceTitle", "View-once guard", "حارس عرض-مرة");
        put("MeeroPickerCardMsg", "Hey 👋", "مرحبا 👋");
        put("MeeroPickerLiveHint", "Live preview - your tap applies instantly", "معاينة حية — لمسة على البطاقة تُطبَّق فوراً");
        put("MeeroPickerRowTitle", "Bubbles & read marks", "الفقاعات وعلامات القراءة");
        put("MeeroPickerSwipeHint", "Swipe the cards sideways for more", "اسحب البطاقات جانبياً لعرض المزيد");
        put("MeeroPickerTabBubbles", "Bubbles", "الفقاعات");
        put("MeeroPickerTabTicks", "Read marks", "علامات القراءة");
        put("MeeroPoolAdd", "Add a text", "إضافة نص");
        put("MeeroPoolDelete", "Delete", "حذف");
        put("MeeroPoolEdit", "Edit", "تعديل");
        put("MeeroPoolInfo", "When on, every general auto-reply picks one random text from this list (looks more human). A per-chat rule always wins - the pool is the general reply only. Write {name} and it becomes the person's name.", "عند التفعيل، كل رد تلقائي عام يختار نصاً عشوائياً من القائمة (يبدو طبيعي أكثر). قاعدة الشخص الخاصة تتغلب دائماً — العشوائي للرد العام فقط. اكتب {name} وتنحط اسم الشخص مكانها.");
        put("MeeroPoolMaster", "Random reply texts", "تفعيل النصوص العشوائية");
        put("MeeroPoolNone", "No texts", "بلا نصوص");
        put("MeeroPoolTitle", "Random replies ⚡", "نصوص عشوائية ⚡");
        put("MeeroPoolWordMany", "texts", "نصوص");
        put("MeeroPoolWordOne", "text", "نص");
        put("MeeroPrivacyAccept", "Accept and continue", "موافق ومتابعة");
        put("MeeroPrivacyBody", "MeeroX is an unofficial Telegram client.\n\n• MeeroX does not collect, store or transmit your personal data to any third party.\n• Your messages travel only between your device and Telegram's servers, exactly as with the official app.\n• Settings, fonts and preferences are stored locally on your device.\n• The source code is open and can be reviewed at any time.\n\nBy continuing you agree to use MeeroX at your own responsibility, and to respect Telegram's Terms of Service.", "ميرو إكس تطبيق غير رسمي لتيليجرام.\n\n• لا يجمع ميرو إكس أي بيانات شخصية ولا يرسلها لأي طرف ثالث.\n• رسائلك تنتقل بين جهازك وخوادم تيليجرام فقط، تماماً كالتطبيق الرسمي.\n• الإعدادات والخطوط والتفضيلات تُحفظ محلياً على جهازك.\n• الكود المصدري مفتوح ويمكن مراجعته في أي وقت.\n\nبالمتابعة فإنك توافق على استخدام ميرو إكس على مسؤوليتك الخاصة، وعلى احترام شروط خدمة تيليجرام.");
        put("MeeroPrivacyTitle", "Privacy Policy", "سياسة الخصوصية");
        put("MeeroRandomEmoji", "Random emoji at the end 🎲", "إيموجي عشوائي بالنهاية 🎲");
        put("MeeroReadAllChats", "Read all chats", "قراءة جميع الدردشات");
        put("MeeroReadAllConfirm", "Every chat (including the archive) will be marked as read. Unread counters will be zeroed and cannot be restored.", "راح تنعلّم كل الدردشات (وياها الأرشيف) كمقروءة، وعدادات غير المقروء تصفّر وما تكدر ترجعها بعد.");
        put("MeeroRelockAfter5Min", "After 5 minutes", "بعد ٥ دقايق");
        put("MeeroRelockAfterMin", "After 1 minute", "بعد دقيقة");
        put("MeeroRelockDelay", "Relock delay", "تأخير إعادة القفل");
        put("MeeroRelockNow", "Instantly", "فوري");
        put("MeeroRulesAdd", "Add rule", "إضافة قاعدة");
        put("MeeroRulesChatFallback", "Chat", "محادثة");
        put("MeeroRulesContentHeader", "Content", "المحتوى");
        put("MeeroRulesDelete", "Delete rule", "حذف القاعدة");
        put("MeeroRulesEdit", "Edit text", "تعديل النص");
        put("MeeroRulesInfo", "A rule replaces the global reply text for that chat only - every other condition (cooldown and all gates) still applies. Tap a rule to edit its text or delete it.", "القاعدة تستبدل نص الرد العام لتلك الدردشة فقط — باقي الشروط (الفاصل بين الردود وكل البوابات) تبقى شغالة عليها. اضغط أي قاعدة لتعديل نصها أو حذفها.");
        put("MeeroRulesNone", "No rules", "بلا قواعد");
        put("MeeroRulesPickPrivate", "Pick a private chat only", "اختر دردشة خاصة فقط");
        put("MeeroRulesTimingHeader", "Timing", "التوقيت");
        put("MeeroRulesTitle", "Per-chat rules", "قواعد لكل شخص");
        put("MeeroRulesWordMany", "rules", "قواعد");
        put("MeeroRulesWordOne", "rule", "قاعدة");
        put("MeeroSearchHint", "Search", "بحث");
        put("MeeroSectionCategories", "Categories", "الأقسام");
        put("MeeroSectionData", "Data", "البيانات");
        put("MeeroSectionInfo", "About", "حول");
        put("MeeroSepFadeInfo", "Hairline separators inside iOS-style cards fade in over 120ms after the menu settles instead of snapping in at once. OFF restores the instant appearance.", "الفواصل الشعرية داخل بطاقات iOS تتلاشى تدريجياً خلال 120ms بعد استقرار القائمة بدل الظهور الفجائي. الإطفاء يرجّع الظهور الفوري.");
        put("MeeroSettingsInfo", "Every MeeroX switch in one place, grouped by what it changes.", "كل مفاتيح MeeroX بمكان واحد، مقسّمة حسب الي تغيّره.");
        put("MeeroSettingsTitle", "MeeroX", "MeeroX");
        put("MeeroSigContinue", "Continue at my own risk", "الاستمرار على مسؤوليتي");
        put("MeeroSigExit", "Exit now", "خروج فوري");
        put("MeeroSigWarningText", "This copy's signature does NOT match the official MeeroX fingerprint. It may be a fake or tampered build that can steal your account.\n\nThis copy:\n%1$s\n\nOfficial fingerprint:\n%2$s\n\nInstall MeeroX only from the developer's official channel.", "توقيع هذه النسخة لا يطابق بصمة MeeroX الرسمية. يمكن أن تكون نسخة مقرصنة أو معدّلة تسرق حسابك وبياناتك.\n\nبصمة هذه النسخة:\n%1$s\n\nالبصمة الرسمية:\n%2$s\n\nحمّل MeeroX فقط من قناة المطور الرسمية.");
        put("MeeroSigWarningTitle", "⚠️ Warning: unofficial copy", "⚠️ تحذير: نسخة غير رسمية");
        put("MeeroSmoothPassInfo", "Pre-warms the first popup menu, the first chat you open and the first chat-list swipe after launching the app. OFF restores the exact previous start-up.", "يسخّن مسبقاً أول قائمة منبثقة وأول دردشة تفتحها وأول سحبة بقائمة الدردشات بعد تشغيل التطبيق. الإطفاء يرجّع التشغيل مثل السابق حرفياً.");
        put("MeeroStatsChartInfo", "Bars show your outgoing messages by hour of day (device local time). The tallest bar carries its value on top.", "الأعمدة تعرض رسائلك الصادرة موزعة على ساعات اليوم (بتوقيت جهازك)، وأعلى عمود مكتوبة قيمته فوقه.");
        put("MeeroStatsDryDays", "%1$d days without a reply", "%1$d يوم بلا رد");
        put("MeeroStatsDryHeader", "Quiet chats", "المحادثات الجافة");
        put("MeeroStatsDryInfo", "Private chats whose last message came from their side - you have not replied yet.", "محادثات خاصة آخر رسالة بيها من طرفهم وأنت بعدك ما رديت عليها.");
        put("MeeroStatsDryToday", "Today", "اليوم");
        put("MeeroStatsDryWord", "chats", "محادثة");
        put("MeeroStatsDryYesterday", "Yesterday", "أمس");
        put("MeeroStatsExport", "Export stats as text", "تصدير الإحصائيات كنص");
        put("MeeroStatsFrom", "since", "منذ");
        put("MeeroStatsHoursHeader", "Your busiest hours", "أنشط ساعاتك");
        put("MeeroStatsInfo", "Computed from messages stored locally on your device only - nothing is ever sent anywhere. Chat-most = exchanged message count. The app-open counter starts at v102 install (Telegram stores no historical open times).", "تُحسب من الرسائل المخزنة محلياً على جهازك فقط — لا يُرسل أي شيء لأي جهة. «أكثر من تحچي وياهم» حسب عدد الرسائل المتبادلة. عدّاد فتح التطبيق يبدأ من تثبيت v102 (تليجرام لا يخزن أوقات الدخول السابقة).");
        put("MeeroStatsLoading", "Computing…", "جاري الحساب…");
        put("MeeroStatsMonth", "Last 30 days", "آخر ٣٠ يوم");
        put("MeeroStatsMsgWord", "messages", "رسالة");
        put("MeeroStatsOpens", "App opens", "مرات فتح التطبيق");
        put("MeeroStatsOverview", "Overview", "نظرة عامة");
        put("MeeroStatsProHeader", "Activity pro", "نشاط برو");
        put("MeeroStatsShare", "Share", "مشاركة");
        put("MeeroStatsTitle", "Activity details", "تفاصيل النشاط");
        put("MeeroStatsToday", "Messages today", "رسائلك اليوم");
        put("MeeroStatsTopHeader", "People you chat with most", "أكثر من تحچي وياهم");
        put("MeeroStatsTotal", "Total stored", "المجموع المخزن");
        put("MeeroStatsWeek", "Last 7 days", "آخر ٧ أيام");
        put("MeeroStoryDownload", "Save to gallery", "تحميل للمعرض");
        put("MeeroStoryDownloadInfo", "Adds a button inside the story menu (⋮) that saves the photo or video straight to your gallery - even when the owner forbids saving. When off, the button disappears exactly like the official Telegram.", "زر داخل قائمة الستوري (⋮) يحفظ الصورة أو الفيديو مباشرة بالمعرض — حتى لو صاحب الستوري مانع الحفظ. عند الإطفاء يختفي الزر كما في تيليجرام الرسمي.");
        put("MeeroStorySaved", "Story saved to gallery", "تم حفظ الستوري بالمعرض");
        put("MeeroSwiftMenusInfo", "Popup menus open and close in about 180ms on an ease-out curve, instead of the inherited 150–350ms mix. OFF restores the original timings.", "القوائم المنبثقة تفتح وتنسد بحدود 180ms وبمنحنى ناعم موحد، بدل القيم الموروثة المتفاوتة (150–350ms). الإطفاء يرجّع التوقيتات الأصلية.");
        put("MeeroSwipeAction", "Chat swipe action", "ضبط إجراء السحب");
        put("MeeroTapMenuInfo", "Tap a message bubble to open its menu. Long press keeps working.", "انقر على فقاعة الرسالة لفتح قائمتها. الضغط المطوّل يبقى يعمل.");
        put("MeeroTickStyle", "Tick shape", "شكل العلامة");
        put("MeeroTicksSwitchInfo", "A dedicated switch for the sent and read ticks: on shows the shape picked in the list below, off brings back the official Android Telegram ticks. Applies after restarting the app.", "مفتاح مستقل لعلامات الإرسال والقراءة: عند التشغيل تُعرض العلامة بالشكل المختار من القائمة بالأسفل، وعند الإطفاء تعود علامة تيليجرام أندرويد الرسمية. يُطبَّق التغيير بعد إعادة تشغيل التطبيق.");
        put("MeeroUnifiedRadiiInfo", "Reply, quote, link and fact-check cards inside bubbles all use the same 12dp corner radius as the MeeroX iOS family. OFF restores per-card radii.", "بطاقات الرد والاقتباس والروابط داخل الفقاعة تستخدم نفس انحناء 12dp مثل عائلة MeeroX الآيفونية. الإطفاء يرجّع الانحناءات الأصلية.");
        put("MeeroUsageGuide", "How to use", "طريقة الاستخدام");
        put("MeeroUsageGuideGotIt", "Got it", "فهمت");
        put("MeeroVaultBrandSub", "Hidden from everywhere - they only live here", "مخفية عن كل مكان - ما تعيش غير هنا");
        put("MeeroVaultCount", "%1$d hidden chats", "%1$d محادثة مخفية");
        put("MeeroVaultEmpty", "No hidden chats.", "ماكو محادثات مخفية.");
        put("MeeroVaultEmptyHint", "Lock a chat from the Chat lock section and it shows up here", "اقفل أي محادثة من قسم «قفل المحادثات» وهي ترجع تظهر هنا");
        put("MeeroVaultGateHint", "Unlock to see your hidden chats", "افتح حتى تشوف محادثاتك المخفية");
        put("MeeroVaultInfo", "One unlock per session, instant relock on exit. Vault access: long-press the Chats tab > \"Hidden chats\".", "فتح وحدة للجلسة والخروج ينقفل فورًا. الوصول: ضغطة مطولة على تبويب «المحادثات» ← «المحادثات المخفية».");
        put("MeeroVaultTitle", "Hidden chats", "المحادثات المخفية");
        put("MeeroVaultUnread", "%1$d unread", "%1$d غير مقروءة");
        put("MeeroVersion", "Version", "الإصدار");
        put("MeeroWatchAdd", "Watch someone", "إضافة شخص للمراقبة");
        put("MeeroWatchAddByHandle", "Enter username or ID", "إدخال يوزر أو أيدي");
        put("MeeroWatchAddFromChats", "Pick from chats", "اختيار من الدردشات");
        put("MeeroWatchAdded", "Now watched ✅", "صار تحت المراقبة ✅");
        put("MeeroWatchAlready", "Already watched", "هذا الشخص قيد المراقبة أصلاً");
        put("MeeroWatchChangedBday", "changed the birthday", "غيّر مواليده");
        put("MeeroWatchChangedBio", "changed the bio", "غيّر البايو");
        put("MeeroWatchChangedName", "changed the name", "غيّر الاسم");
        put("MeeroWatchChangedPhoto", "changed the photo", "غيّر صورته");
        put("MeeroWatchChangedUsername", "changed the username", "غيّر اليوزر");
        put("MeeroWatchHandleHint", "e.g. ali, @ali or 123456789", "مثال: ali أو @ali أو 123456789");
        put("MeeroWatchInfo", "Watch anyone (a contact or any account by username/ID): name, username, bio, birthday or photo changes reach you as instant notifications with full details and a log. Master switch off = nothing watched. Every person has his own ON/OFF switch. Note: bio and birthday sync about hourly; a hidden phone number can never be revealed - Telegram privacy law. Message tracking (its switch above): logs what watched people write in groups you both share - their messages, who they reply to and who replies to them, with second-precision timestamps, newest 150 events, all stored locally on your device only. Responsibility: tracking a person's activity can be sensitive in your circle and under your country's law - use it at your own full responsibility.", "راقب أي شخص (جهة اتصال أو أي حساب بيوزر/أيدي): تغيير الاسم، اليوزر، البايو، المواليد، أو الصورة — يوصلك إشعار فوري مع التفاصيل الكاملة والسجل. المفتاح العام مطفي = لا مراقبة نهائياً. كل شخص بمفتاح تشغيل/إطفاء خاص. ملاحظة: البايو والمواليد بمزامنة دورية تقريباً كل ساعة، ورقم الهاتف المخفي لا يجوز كشفه — خصوصية تليجرام. تتبع الرسائل (سويتشه فوق): يسجل شيكتب المراقبون بالقروبات اللي تجمعكم — رسائلهم، منو يردون عليه، ومنو يرد عليهم — بدقة الثواني وآخر ١٥٠ حدث، وكل شي يبقى بجهازك بس. المسؤولية: تتبع تحركات شخص ممكن يكون حساسًا بمحيطك وبقانون بلدك — استخدمها على مسؤوليتك الكاملة.");
        put("MeeroWatchLogClear", "Clear log", "مسح السجل");
        put("MeeroWatchLogClearConfirm", "Delete all logged changes? (cached photos stay on device)", "تمسح كل التغييرات المسجلة؟ (الصور تبقى محفوظة)");
        put("MeeroWatchLogEmpty", "No changes logged yet", "لا تغييرات مسجلة بعد");
        put("MeeroWatchLogInfo", "Newest 150 changes. Photos are cached on your device - tap a photo change to view or download it.", "آخر ١٥٠ تغيير، الأحدث أولاً. الصور محفوظة على جهازك — اضغط أي تغيير صورة لعرضها أو تنزيلها.");
        put("MeeroWatchLogRow", "Change log", "سجل التغييرات");
        put("MeeroWatchLogTitle", "Change log", "سجل التغييرات");
        put("MeeroWatchMsgIn", "in", "في");
        put("MeeroWatchMsgMedia", "media", "وسائط");
        put("MeeroWatchMsgNotify", "Instant alert on tracked messages", "تنبيه فوري عند رسالة متتبعة");
        put("MeeroWatchMsgReplyingTo", "replying to", "ردًا على");
        put("MeeroWatchMsgTheirMsg", "their message", "نص رسالته");
        put("MeeroWatchMsgTrack", "Track messages in shared groups", "تتبع رسائل المراقبين بالمجموعات");
        put("MeeroWatchNoOne", "Nobody watched yet - add above", "ماكو أحد بالمراقبة — أضف من فوق");
        put("MeeroWatchNotFound", "Account not found (numeric ID works only for accounts already known to you)", "ما لكيت الحساب (بالأيدي: فقط إذا الحساب معروف عندك مسبقاً)");
        put("MeeroWatchPhotoHint", "tap to view photos", "اضغط لعرض الصور");
        put("MeeroWatchPhotoMissing", "Photos unavailable for this change", "الصور غير متوفرة لهذا التغيير");
        put("MeeroWatchPhotoSave", "Save to gallery", "تنزيل للمعرض");
        put("MeeroWatchPhotoViewNew", "View new photo", "عرض الصورة الجديدة");
        put("MeeroWatchPhotoViewOld", "View old photo", "عرض الصورة القديمة");
        put("MeeroWatchRemove", "Remove from watch", "حذف من المراقبة");
        put("MeeroWatchSaveFailed", "Save failed (check storage permission)", "فشل الحفظ (تأكد من إذن التخزين)");
        put("MeeroWatchSaved", "Saved to gallery ✅", "انحفظت بالمعرض ✅");
        put("MeeroWatchTitle", "Account watch", "مراقبة الحسابات");
        put("MeeroWatchWatchedHeader", "Watched people", "المراقَبون");
        put("MeeroWatchWhatMsg", "sent a message", "أرسل رسالة");
        put("MeeroWatchWhatMsgReply", "replied to a message", "رد على رسالة");
        put("MeeroWatchWhatReplyTo", "got a reply", "لقى رد على رسالته");
        put("MixerAccent", "Accent color", "اللون المميز");
        put("MixerAccentBlue", "iOS Blue", "أزرق آيفون");
        put("MixerAccentGold", "Gold", "ذهبي");
        put("MixerAccentMint", "Mint Green", "أخضر نعناعي");
        put("MixerAccentOrange", "Orange", "برتقالي");
        put("MixerAccentRed", "Red", "أحمر");
        put("MixerAccentRose", "Meero Rose", "وردة ميرو");
        put("MixerAccentSky", "Sky", "سماوي");
        put("MixerAccentViolet", "Violet", "بنفسجي");
        put("MixerApplied", "Mixed theme applied — it is now listed with your themes and can be shared as a file.", "انطبق الثيم المخلوط — صار مدرجاً مع ثيماتك وتقدر تشاركه كملف.");
        put("MixerApply", "Mix & apply this theme", "اخلط وطبّق هذا الثيم");
        put("MixerBackground", "Background", "الخلفية");
        put("MixerBgAmoled", "AMOLED Black", "أسود AMOLED");
        put("MixerBgGraphite", "Graphite Dark", "فحمي غامق");
        put("MixerBgMidnight", "Midnight Blue", "كحلي منتصف الليل");
        put("MixerBgPaper", "Paper White", "أبيض ورقي");
        put("MixerFailed", "Could not generate the theme file. Please try again.", "ما قدرنا نولّد ملف الثيم، جرّب مرة ثانية.");
        put("MixerHeader", "Compose your theme by feel", "كوّن ثيمك على ذوقك");
        put("MixerHubTitle", "Theme Mixer (MeeroX)", "صانع الثيمات (ميرو)");
        put("MixerInBubble", "Incoming bubble", "فقاعة الطرف الثاني");
        put("MixerInBubbleBlack", "Pure AMOLED black", "أسود AMOLED خالص");
        put("MixerInBubbleFollow", "Follow background style", "يتبع نمط الخلفية");
        put("MixerInBubbleGraphite", "Graphite (iOS gray)", "فحمي (رمادي آيفون)");
        put("MixerInBubbleTinted", "Tinted by my accent", "مخلوط بلوني المميز");
        put("MixerInfo", "Pick accent, background and the incoming-bubble color; the preview above updates live. Applying creates a real theme file named \"MeeroX Mix\" through Telegram's official pipeline — it appears with your other themes, syncs nowhere private, and going back is one tap from Telegram's theme screen. Your chat wallpaper is never touched.", "اختار اللون المميز والخلفية ولون فقاعة الطرف الثاني؛ المعاينة فوق تتحدث مباشرة. التطبيق يولّد ملف ثيم حقيقي باسم \"MeeroX Mix\" عبر القناة الرسمية لتيليجرام — يظهر مع ثيماتك الأخرى، والرجوع لثيمك السابق ضغطة من شاشة ثيمات تيليجرام. خلفية محادثتك الحالية لا تتغير.");
        put("MixerRestore", "Back to your other themes", "ارجع لثيماتك الأخرى");
        put("MixerTitle", "Theme Mixer", "صانع الثيمات");
        put("SmartFolderActiveGroups", "Active groups", "مجموعات نشطة");
        put("SmartFolderActiveGroupsRule", "Rule: type = group + not muted", "القاعدة: نوع=مجموعة + بدون كتم");
        put("SmartFolderBots", "Bots & services", "البوتات والخدمات");
        put("SmartFolderBotsRule", "Rule: type = bot (archived excluded)", "القاعدة: نوع=بوت (باستثناء المؤرشف)");
        put("SmartFolderCreate", "Create folder", "إنشاء المجلد");
        put("SmartFolderDone", "Created ✓ — manage it from Settings → Folders", "مُنشأ ✓ — دبّره من الإعدادات ← المجلدات");
        put("SmartFolderExists", "This smart folder already exists in your folders list.", "هذا المجلد الذكي موجود أصلاً بقائمة مجلداتك.");
        put("SmartFolderFamily", "Family & contacts", "العائلة وجهات الاتصال");
        put("SmartFolderFamilyRule", "Rule: type = contact (archived excluded)", "القاعدة: نوع=جهة اتصال (باستثناء المؤرشف)");
        put("SmartFolderUnreadChannels", "Unread channels", "قنوات غير مقروءة");
        put("SmartFolderUnreadChannelsRule", "Rule: type = channel + has unread + not muted", "القاعدة: نوع=قناة + بها غير مقروء + بدون كتم");
        put("SmartFolderUnreadChats", "Unread chats", "محادثات غير مقروءة");
        put("SmartFolderUnreadChatsRule", "Rule: contacts + non-contacts + groups with unread", "القاعدة: جهات اتصال + غير معروفين + مجموعات بها غير مقروء");
        put("SmartFoldersHeader", "One-tap folders, built from rules", "مجلدات بلمسة وحدة، مبنية بقواعد");
        put("SmartFoldersHubTitle", "Smart Folders (MeeroX)", "المجلدات الذكية (ميرو)");
        put("SmartFoldersInfo", "Each preset creates a real synced Telegram folder composed of rules (type, unread, muted). It appears in your chats list immediately, syncs to every device, and can be edited or removed from Telegram's regular Folders settings. Count-based rules (e.g. \"unread more than 5\") cannot live in the server engine, so they are approximated by \"unread\".", "كل قالب ينشئ مجلد تيليجرام حقيقي متزامن مركّب من قواعد (النوع، غير المقروء، المكتوم). يظهر بقائمة محادثاتك فوراً، يتزامن لكل أجهزتك، وتقدر تعدله أو تحذفه من إعدادات «المجلدات» الرسمية. القواعد العددية (مثلاً «غير مقروء أكثر من 5») ما يدعمها محرك السيرفر، فاستبدلناها بـ«غير مقروء».");
        put("SmartFoldersTitle", "Smart Folders", "المجلدات الذكية");
        put("StyleIos", "iOS", "آي أو إس");
        put("meeroAmoledBubbles", "AMOLED black bubbles", "فقاعات سوداء AMOLED");
        put("meeroAmoledStroke", "AMOLED bubble edge", "حد شعرة لفقاعات AMOLED");
        put("meeroAutoJanitor", "Auto cache janitor", "حارس التخزين التلقائي");
        put("meeroBubbleDesc0", "The original Telegram for Android bubble with its small tail.", "فقاعة تيليجرام أندرويد الأصلية بذيلها الصغير.");
        put("meeroBubbleDesc1", "The real iPhone bubble: 18dp corners and the true crescent tail, decoded from the official Telegram-iOS source.", "فقاعة الآيفون الحقيقية: زوايا 18 والذيل الهلالي الأصلي، مستخرجة من مصدر تيليجرام iOS الرسمي.");
        put("meeroBubbleDesc2", "Large 18dp rounded corners with a clean, tail-free edge.", "زوايا مستديرة كبيرة 18 بحدود نظيفة بدون ذيل.");
        put("meeroBubbleDesc3", "Fully rounded pill ends, no tail.", "أطراف مستديرة بالكامل على شكل كبسولة، بدون ذيل.");
        put("meeroBubbleDesc4", "Soft 10dp corners with a small wedge tail.", "زوايا ناعمة 10 مع ذيل مثلث صغير.");
        put("meeroBubbleDesc5", "Crisp 6dp corners, no tail. Minimal and flat.", "زوايا مستقيمة 6 بدون ذيل، طابع مينيمال نظيف.");
        put("meeroBubbleDesc6", "Extra-rounded 22dp, no tail. Instagram DM vibe.", "استدارة مفرطة 22 بدون ذيل، مثل رسائل إنستا.");
        put("meeroBubbleDesc7", "Soft 8dp corners with a tiny curved nub.", "زوايا ناعمة 8 مع طرف منحني صغير مثل واتساب.");
        put("meeroBubbleName0", "Stock (official)", "الافتراضي الرسمي");
        put("meeroBubbleName1", "Official iOS bubble", "فقاعة iOS الرسمية");
        put("meeroBubbleName2", "Modern iOS, no tail", "iOS حديث بدون ذيل");
        put("meeroBubbleName3", "Capsule", "كبسولة");
        put("meeroBubbleName4", "Classic", "كلاسيك");
        put("meeroBubbleName5", "Sharp modern", "حادة عصرية");
        put("meeroBubbleName6", "Instagram", "إنستغرام");
        put("meeroBubbleName7", "WhatsApp", "واتساب");
        put("meeroCards", "Rounded settings cards", "بطاقات إعدادات مدوّرة");
        put("meeroChatsMenuFog", "Fog behind chats popup", "طمس خلف منبثقة زر المحادثات");
        put("meeroDialogsStyle", "iOS chats header", "رأس المحادثات بنمط iOS");
        put("meeroFlexWidth", "Flexible menu width", "عرض مرن للقوائم");
        put("meeroGhostSwipeRead", "Ghost read by swipe", "قراءة شبحية بالسحب");
        put("meeroGlassBorders", "Glass edges", "حدود زجاجية");
        put("meeroGlassSettings", "Meero glass design", "تصميم ميرو الزجاجي");
        put("meeroGlassSwitches", "Meero glass switches", "مفاتيح ميرو الزجاجية");
        put("meeroIosAlerts", "iOS dialog rules", "فواصل الحوارات مثل الآيفون");
        put("meeroIosAnim", "iOS message animation", "أنميشن الرسائل بنمط iOS");
        put("meeroIosCall", "iOS call buttons", "أزرار المكالمة مثل الآيفون");
        put("meeroIosCode", "iOS verification screen", "شاشة رمز التحقق بنمط iOS");
        put("meeroIosFastScroll", "iOS index bar", "شريط الأحرف بنمط iOS");
        put("meeroIosHaptics", "iOS haptic weights", "شدّات الاهتزاز بنمط iOS");
        put("meeroIosIcons", "Official iOS icons", "أيقونات iOS الرسمية");
        put("meeroIosInputPill", "iOS-style composer bar", "شريط الكتابة مثل آيفون");
        put("meeroIosIntro", "iOS welcome screen", "شاشة الترحيب بنمط iOS");
        put("meeroIosLoading", "iOS download indicator", "مؤشّر التحميل بنمط iOS");
        put("meeroIosMainMenu", "iOS main menu", "قائمة الرئيسية بنمط آيفون");
        put("meeroIosMediaGrid", "iOS media duration", "مدة الفيديو مثل الآيفون");
        put("meeroIosMenuAnim", "Menus grow from the item", "القوائم تكبر من العنصر");
        put("meeroIosMsgMenu", "iOS message context menu", "قائمة سياق الرسالة بنمط آيفون");
        put("meeroIosPopupMenu", "iOS popup menus", "قوائم منبثقة بنمط آيفون");
        put("meeroIosRow", "iOS chat rows", "صفوف المحادثات بنمط iOS");
        put("meeroIosSearch", "iOS search field", "حقل البحث بنمط iOS");
        put("meeroIosSelection", "iOS selection icons", "أيقونات التحديد بنمط iOS");
        put("meeroIosShadows", "Soft card shadows", "ظلال ناعمة للبطاقات");
        put("meeroIosSounds", "iOS message sounds", "أصوات الرسائل بنمط iOS");
        put("meeroIosStories", "iOS stories row", "صف الستوريات مثل الآيفون");
        put("meeroIosWaveform", "iOS voice waveform", "موجة الصوت بنمط iOS");
        put("meeroJanitorAge", "Delete files older than", "حذف الملفات الأقدم من");
        put("meeroJanitorLimit", "Cache size limit", "الحد الأقصى للكاش");
        put("meeroJanitorMode", "Cleaning schedule", "جدولة التنظيف");
        put("meeroMenuBlur", "Blur behind context menu", "طمس خلف قائمة السياق");
        put("meeroSepFade", "Soft separator fade-in", "تلاشي ناعم للفواصل");
        put("meeroSmoothPass", "Smooth-start pack", "حزمة السلاسة");
        put("meeroStoryDownload", "Save stories to gallery", "تحميل الستوري للمعرض");
        put("meeroSwiftMenus", "iOS menu speed", "سرعة القوائم الآيفونية");
        put("meeroTapMenu", "Open menu with a single tap", "فتح القائمة بنقرة واحدة");
        put("meeroTickDesc0", "The exact same thin ticks you already have, completely untouched - the default.", "نفس علامة الصح الرفيعة الحالية تماماً وكما هي — الاختيار الافتراضي.");
        put("meeroTickDesc1", "One heart when sent, a pair of hearts when the message is read.", "قلب عند الإرسال، وقلبان متجاوران عند قراءة الرسالة.");
        put("meeroTickDesc10", "A shield when sent, two shields when read.", "درع عند الإرسال، ودرعان عند القراءة.");
        put("meeroTickDesc11", "The infinity loop when sent, two loops when read.", "رمز اللانهاية عند الإرسال، ورمزان عند القراءة.");
        put("meeroTickDesc12", "A little paw when sent, two paws when read.", "مخلب لطيف عند الإرسال، ومخلبان عند القراءة.");
        put("meeroTickDesc13", "A tree leaf when sent, two leaves when read.", "ورقة شجر عند الإرسال، وورقتان عند القراءة.");
        put("meeroTickDesc14", "A fire flame when sent, two flames when read.", "شعلة نار عند الإرسال، وشعلتان عند القراءة.");
        put("meeroTickDesc15", "A paper plane when sent, two planes when read.", "طائرة ورقية عند الإرسال، وطائرتان عند القراءة.");
        put("meeroTickDesc2", "A five-point star when sent, two stars when read.", "نجمة خماسية عند الإرسال، ونجمتان عند القراءة.");
        put("meeroTickDesc3", "A simple round dot when sent, two dots when read.", "نقطة دائرية بسيطة عند الإرسال، ونقطتان عند القراءة.");
        put("meeroTickDesc4", "A four-point sparkle when sent, two sparkles when read.", "شرارة رباعية الأطراف عند الإرسال، وشرارتان عند القراءة.");
        put("meeroTickDesc5", "A lightning bolt when sent, two bolts when read.", "صاعقة عند الإرسال، وصاعقتان عند القراءة.");
        put("meeroTickDesc6", "A crescent moon when sent, two crescents when read.", "هلال عند الإرسال، وهلالان متداخلان عند القراءة.");
        put("meeroTickDesc7", "A diamond when sent, two diamonds when read.", "ماسة عند الإرسال، وماسَتان عند القراءة.");
        put("meeroTickDesc8", "A droplet when sent, two droplets when read.", "قطرة عند الإرسال، وقطرتان عند القراءة.");
        put("meeroTickDesc9", "A bell when sent, two bells when read.", "جرس عند الإرسال، وجرسان عند القراءة.");
        put("meeroTickName0", "Current iOS tick", "علامة iOS الحالية");
        put("meeroTickName1", "Hearts", "القلوب");
        put("meeroTickName10", "Shield", "الدرع");
        put("meeroTickName11", "Infinity", "اللانهاية");
        put("meeroTickName12", "Paw", "المخلب");
        put("meeroTickName13", "Leaf", "الورقة");
        put("meeroTickName14", "Flame", "اللهب");
        put("meeroTickName15", "Paper plane", "الطائرة");
        put("meeroTickName2", "Stars", "النجوم");
        put("meeroTickName3", "Dots", "النقاط");
        put("meeroTickName4", "Sparkle", "الشرارة");
        put("meeroTickName5", "Bolt", "البرق");
        put("meeroTickName6", "Crescent", "الهلال");
        put("meeroTickName7", "Gem", "الألماس");
        put("meeroTickName8", "Water drop", "قطرة الماء");
        put("meeroTickName9", "Bell", "الجرس");
        put("meeroTicksSwitch", "Custom read ticks", "علامات قراءة مخصصة");
        put("meeroUnifiedRadii", "Unified card corners", "زوايا موحدة للبطاقات");
        put("MeeroIconMBold", "Bold M", "M عريض");
        put("MeeroIconMMarker", "Marker M", "M ناعم");
        put("MeeroIconMTile", "Tile M", "بلاطة M");
        put("MeeroIconMDuo", "Rose Duo", "وردي ثنائي M");
    }

    private static void put(String key, String en, String ar) {
        MAP.put(key, new String[]{en, ar});
    }

    private static boolean isArabic() {
        try {
            final Locale l = org.telegram.messenger.ApplicationLoader
                    .applicationContext.getResources().getConfiguration().locale;
            return l != null && "ar".equals(l.getLanguage());
        } catch (Throwable t) {
            return false;
        }
    }

    /** Drop-in for LocaleController.getString(R.string.X) / getString(R.string.X). */
    public static String s(String key) {
        final String[] v = MAP.get(key);
        if (v == null) {
            return key;
        }
        return isArabic() && v[1] != null ? v[1] : (v[0] != null ? v[0] : v[1]);
    }

    /**
     * v176: title resolver for the generic config-cell machinery. Those
     * cells resolve LocaleController.getString(key) with a RUNTIME key
     * (the config item name), which goes through getIdentifier() - dead
     * since v171 removed the XML resources - so the cells silently fell
     * back to displaying the raw English key (his screenshot report:
     * meeroGlassSwitches & friends, owned). Order here: vault map ->
     * upstream resource (Neko/Telegram keys still live in resources) ->
     * the key itself, matching the cells' historical last resort.
     */
    public static String title(String key) {
        final String[] v = MAP.get(key);
        if (v != null) {
            return isArabic() && v[1] != null ? v[1] : (v[0] != null ? v[0] : v[1]);
        }
        try {
            final String up = org.telegram.messenger.LocaleController.nullable(
                    org.telegram.messenger.LocaleController.getString(key));
            if (up != null) {
                return up;
            }
        } catch (Throwable ignored) {
        }
        return key;
    }

    /** Drop-in for getString(R.string.X, ...) / LocaleController.formatString(R.string.X, ...). */
    public static String f(String key, Object... args) {
        final String raw = s(key);
        try {
            return String.format(raw, args);
        } catch (Throwable t) {
            return raw;
        }
    }
}
