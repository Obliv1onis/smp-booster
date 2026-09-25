package com.smpBooster.i18n;

import com.smpBooster.SmpBooster;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class LanguageService implements CommandExecutor, TabCompleter {
    private final SmpBooster plugin;
    private final Map<String, String> zh = new HashMap<>();
    private final Map<String, String> en = new HashMap<>();
    private final Map<String, String> ja = new HashMap<>();
    private final Map<String, String> de = new HashMap<>();
    private final Map<String, String> es = new HashMap<>();
    private String language;

    public LanguageService(SmpBooster plugin) {
        this.plugin = plugin;
        this.language = normalize(plugin.getConfig().getString("messages.language", "zh"));
        loadMessages();
    }

    public String text(String key, Object... arguments) {
        Map<String, String> messages = switch (language) {
            case "en" -> en;
            case "ja" -> ja;
            case "de" -> de;
            case "es" -> es;
            default -> zh;
        };
        String value = messages.getOrDefault(key, key);
        return arguments.length == 0 ? value : String.format(Locale.ROOT, value, arguments);
    }

    public String code() {
        return language;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!sender.isOp()) {
            sender.sendMessage(text("op-only"));
            return true;
        }
        if (args.length != 1 || !List.of("zh", "en", "ja", "de", "es").contains(args[0].toLowerCase(Locale.ROOT))) {
            sender.sendMessage(text("language-usage"));
            return true;
        }
        language = normalize(args[0]);
        plugin.getConfig().set("messages.language", language);
        plugin.saveConfig();
        sender.sendMessage(text("language-changed"));
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                                 @NotNull String alias, @NotNull String[] args) {
        if (!sender.isOp() || args.length != 1) return Collections.emptyList();
        String prefix = args[0].toLowerCase(Locale.ROOT);
        return List.of("zh", "en", "ja", "de", "es").stream().filter(value -> value.startsWith(prefix)).toList();
    }

    private String normalize(String value) {
        if (value == null) return "zh";
        String normalized = value.toLowerCase(Locale.ROOT);
        return List.of("zh", "en", "ja", "de", "es").contains(normalized) ? normalized : "zh";
    }

    private void loadMessages() {
        put("op-only", "§c此指令仅限 OP 使用。", "§cThis command is restricted to operators.", "§cこのコマンドは OP のみ使用できます。", "§cDieser Befehl ist nur für Operatoren verfügbar.", "§cEste comando está restringido a operadores.");
        put("language-usage", "§e用法: /language <zh|en|ja|de|es>", "§eUsage: /language <zh|en|ja|de|es>", "§e使い方: /language <zh|en|ja|de|es>", "§eVerwendung: /language <zh|en|ja|de|es>", "§eUso: /language <zh|en|ja|de|es>");
        put("language-changed", "§a插件语言已切换为中文。", "§aPlugin language changed to English.", "§aプラグインの言語を日本語に変更しました。", "§aDie Plugin-Sprache wurde auf Deutsch geändert.", "§aEl idioma del plugin se cambió a español.");
        put("ender-pearl-disabled", "末影珍珠传送已被服务器禁用。", "Ender pearl teleportation is disabled on this server.", "このサーバーではエンダーパールによるテレポートが無効です。", "Enderperlen-Teleportation ist auf diesem Server deaktiviert.", "El teletransporte con perlas de ender está desactivado en este servidor.");
        put("chorus-fruit-disabled", "紫颂果传送已被服务器禁用。", "Chorus fruit teleportation is disabled on this server.", "このサーバーではコーラスフルーツによるテレポートが無効です。", "Chorusfrucht-Teleportation ist auf diesem Server deaktiviert.", "El teletransporte con fruta chorus está desactivado en este servidor.");

        put("silent-usage", "§e用法: /silentac [on|off|toggle|status]", "§eUsage: /silentac [on|off|toggle|status]", "§e使い方: /silentac [on|off|toggle|status]", "§eVerwendung: /silentac [on|off|toggle|status]", "§eUso: /silentac [on|off|toggle|status]");
        put("silent-status-on", "§e静默反作弊当前: §a开启", "§eSilent anti-cheat: §aON", "§eサイレントアンチチート: §aオン", "§eStiller Anti-Cheat: §aAN", "§eAntitrampas silencioso: §aACTIVADO");
        put("silent-status-off", "§e静默反作弊当前: §c关闭", "§eSilent anti-cheat: §cOFF", "§eサイレントアンチチート: §cオフ", "§eStiller Anti-Cheat: §cAUS", "§eAntitrampas silencioso: §cDESACTIVADO");
        put("silent-enabled", "§e静默反作弊已§a开启§e：只记录并通知 OP。", "§eSilent anti-cheat is §aON§e: violations are logged and operators are alerted.", "§eサイレントアンチチートを§aオン§eにしました。違反を記録し OP に通知します。", "§eStiller Anti-Cheat ist §aAN§e: Verstöße werden protokolliert und Operatoren benachrichtigt.", "§eEl antitrampas silencioso está §aACTIVADO§e: se registran las infracciones y se avisa a los operadores.");
        put("silent-disabled", "§e静默反作弊已§c关闭§e：确认作弊后将按配置封禁。", "§eSilent anti-cheat is §cOFF§e: confirmed cheaters will be banned.", "§eサイレントアンチチートを§cオフ§eにしました。チート確定時は BAN します。", "§eStiller Anti-Cheat ist §cAUS§e: Bestätigte Cheater werden gebannt.", "§eEl antitrampas silencioso está §cDESACTIVADO§e: los tramposos confirmados serán expulsados permanentemente.");
        put("whitelist-usage", "§e用法: /acwhitelist <add|remove|list> [玩家]", "§eUsage: /acwhitelist <add|remove|list> [player]", "§e使い方: /acwhitelist <add|remove|list> [プレイヤー]", "§eVerwendung: /acwhitelist <add|remove|list> [Spieler]", "§eUso: /acwhitelist <add|remove|list> [jugador]");
        put("whitelist-list", "§e反作弊白名单（%d）: §f%s", "§eAnti-cheat whitelist (%d): §f%s", "§eアンチチートのホワイトリスト（%d）: §f%s", "§eAnti-Cheat-Whitelist (%d): §f%s", "§eLista blanca del antitrampas (%d): §f%s");
        put("none", "无", "None", "なし", "Keine", "Ninguno");
        put("whitelist-added", "§a已加入反作弊白名单: %s", "§aAdded to anti-cheat whitelist: %s", "§aアンチチートのホワイトリストに追加しました: %s", "§aZur Anti-Cheat-Whitelist hinzugefügt: %s", "§aAñadido a la lista blanca del antitrampas: %s");
        put("whitelist-removed", "§a已移出反作弊白名单: %s", "§aRemoved from anti-cheat whitelist: %s", "§aアンチチートのホワイトリストから削除しました: %s", "§aVon der Anti-Cheat-Whitelist entfernt: %s", "§aEliminado de la lista blanca del antitrampas: %s");
        put("whitelist-unchanged", "§e白名单无需更改: %s", "§eWhitelist already unchanged: %s", "§eホワイトリストの変更は不要です: %s", "§eWhitelist ist bereits unverändert: %s", "§eLa lista blanca no necesita cambios: %s");
        put("ac-alert", "[SilentAC] %s 疑似使用 %s（VL %s）", "[SilentAC] %s may be using %s (VL %s)", "[SilentAC] %s が %s を使用している可能性があります（VL %s）", "[SilentAC] %s verwendet möglicherweise %s (VL %s)", "[SilentAC] %s podría estar usando %s (VL %s)");

        put("villager-usage", "§e用法: /infvillager [on|off|toggle|status]", "§eUsage: /infvillager [on|off|toggle|status]", "§e使い方: /infvillager [on|off|toggle|status]", "§eVerwendung: /infvillager [on|off|toggle|status]", "§eUso: /infvillager [on|off|toggle|status]");
        put("villager-status-on", "§e村民无限补货当前: §a开启", "§eInfinite villager trades: §aON", "§e村人の無限取引: §aオン", "§eUnendliche Dorfbewohner-Handel: §aAN", "§eComercio infinito de aldeanos: §aACTIVADO");
        put("villager-status-off", "§e村民无限补货当前: §c关闭", "§eInfinite villager trades: §cOFF", "§e村人の無限取引: §cオフ", "§eUnendliche Dorfbewohner-Handel: §cAUS", "§eComercio infinito de aldeanos: §cDESACTIVADO");
        put("villager-enabled", "§a村民无限补货已开启。§7（已恢复 %d 个已加载村民的交易）", "§aInfinite villager trades enabled. §7(Restored %d loaded villagers.)", "§a村人の無限取引をオンにしました。§7（読み込み済みの村人 %d 体を補充しました）", "§aUnendliche Dorfbewohner-Handel aktiviert. §7(%d geladene Dorfbewohner aufgefüllt.)", "§aComercio infinito de aldeanos activado. §7(Se repusieron %d aldeanos cargados.)");
        put("villager-disabled", "§c村民无限补货已关闭。§7之后的交易将恢复原版次数限制。", "§cInfinite villager trades disabled. §7Future trades use Vanilla limits.", "§c村人の無限取引をオフにしました。§7以降の取引にはバニラの回数制限が適用されます。", "§cUnendliche Dorfbewohner-Handel deaktiviert. §7Künftige Handel verwenden die Vanilla-Limits.", "§cComercio infinito de aldeanos desactivado. §7Los próximos intercambios usarán los límites originales.");

        put("paperfix-usage", "§e用法: /paperfix [on|off|toggle|status]", "§eUsage: /paperfix [on|off|toggle|status]", "§e使い方: /paperfix [on|off|toggle|status]", "§eVerwendung: /paperfix [on|off|toggle|status]", "§eUso: /paperfix [on|off|toggle|status]");
        put("paperfix-status-on", "§ePaper 原版漏洞修复当前: §a开启", "§ePaper Vanilla exploit fixes: §aON", "§ePaper のバニラ脆弱性修正: §aオン", "§ePaper-Fixes für Vanilla-Exploits: §aAN", "§eCorrecciones de exploits Vanilla de Paper: §aACTIVADAS");
        put("paperfix-status-off", "§ePaper 原版漏洞修复当前: §c关闭", "§ePaper Vanilla exploit fixes: §cOFF", "§ePaper のバニラ脆弱性修正: §cオフ", "§ePaper-Fixes für Vanilla-Exploits: §cAUS", "§eCorrecciones de exploits Vanilla de Paper: §cDESACTIVADAS");
        put("paperfix-status-error", "§e无法读取 Paper 运行时状态；请查看 config/paper-global.yml。", "§eUnable to read Paper runtime state; check config/paper-global.yml.", "§ePaper の実行時状態を読み取れません。config/paper-global.yml を確認してください。", "§ePaper-Laufzeitstatus konnte nicht gelesen werden; prüfe config/paper-global.yml.", "§eNo se pudo leer el estado de Paper; revisa config/paper-global.yml.");
        put("paperfix-write-error", "§c无法写入 Paper 配置，开关未执行。请查看后台日志。", "§cCould not write Paper configuration. Check the server log.", "§cPaper の設定を書き込めませんでした。サーバーログを確認してください。", "§cPaper-Konfiguration konnte nicht geschrieben werden. Prüfe das Serverprotokoll.", "§cNo se pudo escribir la configuración de Paper. Revisa el registro del servidor.");
        put("paperfix-enabled", "§aPaper 原版漏洞修复已开启。§7已禁用 attribute swap、刷钩机及其他受控漏洞。", "§aPaper Vanilla exploit fixes enabled. §7Attribute swap, tripwire duping, and other controlled exploits are blocked.", "§aPaper のバニラ脆弱性修正をオンにしました。§7属性スワップ、トリップワイヤー複製などを防止します。", "§aPaper-Fixes für Vanilla-Exploits aktiviert. §7Attributtausch, Stolperdraht-Duplizierung und weitere Exploits sind blockiert.", "§aCorrecciones de exploits Vanilla de Paper activadas. §7Se bloquearon el intercambio de atributos, la duplicación con cuerda trampa y otros exploits.");
        put("paperfix-disabled", "§cPaper 原版漏洞修复已关闭。§e原版复制及 attribute swap 等漏洞现在被允许，请谨慎使用。", "§cPaper Vanilla exploit fixes disabled. §eVanilla dupes and attribute swap are now allowed; use caution.", "§cPaper のバニラ脆弱性修正をオフにしました。§eバニラの複製や属性スワップが許可されます。注意してください。", "§cPaper-Fixes für Vanilla-Exploits deaktiviert. §eVanilla-Duplizierungen und Attributtausch sind nun erlaubt; Vorsicht!", "§cCorrecciones de exploits Vanilla de Paper desactivadas. §eAhora se permiten duplicaciones Vanilla e intercambio de atributos; ten cuidado.");
        put("paperfix-live", "§7Paper 运行时配置和配置文件均已更新。", "§7Paper runtime and configuration files were updated.", "§7Paper の実行時設定と設定ファイルを更新しました。", "§7Paper-Laufzeit und Konfigurationsdateien wurden aktualisiert.", "§7Se actualizaron la configuración en ejecución y los archivos de Paper.");
        put("paperfix-restart", "§e配置文件已更新，但运行时更新失败；请重启服务器使全部设置生效。", "§eFiles were updated, but live update failed; restart the server to apply every setting.", "§e設定ファイルは更新されましたが、実行時更新に失敗しました。すべて反映するには再起動してください。", "§eDateien wurden aktualisiert, aber die Laufzeitaktualisierung schlug fehl; starte den Server neu.", "§eLos archivos se actualizaron, pero falló la actualización en vivo; reinicia el servidor.");

        put("check.reach", "超距离攻击", "Reach", "リーチ", "Reichweite", "Alcance");
        put("check.killaura", "自动攻击/KillAura", "KillAura", "KillAura", "KillAura", "KillAura");
        put("check.crystalaura", "CrystalAura", "CrystalAura", "CrystalAura", "CrystalAura", "CrystalAura");
        put("check.flight", "飞行", "Flight", "飛行", "Fliegen", "Vuelo");
        put("check.elytra-flight", "鞘翅平飞", "Elytra flight", "エリトラ水平飛行", "Elytra-Flug", "Vuelo con élitros");
    }

    private void put(String key, String chinese, String english, String japanese, String german, String spanish) {
        zh.put(key, chinese);
        en.put(key, english);
        ja.put(key, japanese);
        de.put(key, german);
        es.put(key, spanish);
    }
}
