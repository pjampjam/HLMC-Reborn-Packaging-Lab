"""Enchantment Descriptions text for modded enchantments that ship none (written into the extras jar assets).

Nemo's English text comes from that mod's own ".description" keys; Farmer's Delight and Runeforged text follows
their enchantment data and documentation. Run from addons/extras.
"""
import json
from pathlib import Path

D = {
    "farmersdelight:backstabbing": ("Hits from behind deal 40% more damage, plus 20% per extra level.",
                                   "Удары со спины наносят на 40% больше урона, плюс 20% за каждый уровень.",
                                   "Sitieni no mugurpuses nodara par 40% vairāk bojājumu, plus 20% par katru nākamo līmeni."),
    "nemos_enchantments:camouflage": ("Monsters notice you from a shorter distance.", "Монстры замечают тебя с меньшего расстояния.", "Monstri tevi pamana no mazāka attāluma."),
    "nemos_enchantments:climber": ("Lets you climb ladders and scaffolding faster.", "Быстрее лазаешь по лестницам и строительным лесам.", "Ātrāk kāp pa kāpnēm un sastatnēm."),
    "nemos_enchantments:collector": ("Places mined blocks directly into your inventory.", "Добытые блоки сразу попадают в инвентарь.", "Izraktie bloki uzreiz nonāk tavā inventārā."),
    "nemos_enchantments:farmers_knowledge": ("Prevents you from harvesting crops before they are fully grown.", "Не даёт собрать урожай, пока он не созрел.", "Neļauj novākt ražu, pirms tā pilnībā izaugusi."),
    "nemos_enchantments:felling": ("Fells an entire tree by breaking one log.", "Срубает всё дерево, если сломать одно бревно.", "Nocērt visu koku, nocērtot vienu baļķi."),
    "nemos_enchantments:head_hunter": ("Killed mobs have a chance to drop their heads.", "Убитые мобы могут уронить свою голову.", "Nogalinātiem mobiem ir iespēja nomest savu galvu."),
    "nemos_enchantments:magma_walker": ("Turns nearby lava into magma while you walk.", "Превращает лаву рядом в магму, пока ты идёшь.", "Ejot pārvērš tuvējo lavu magmā."),
    "nemos_enchantments:reaper": ("Harvests multiple nearby crops at once.", "Собирает сразу несколько растений рядом.", "Novāc vairākus tuvējos augus uzreiz."),
    "nemos_enchantments:replanting": ("Replants harvested crops.", "Сажает собранные растения заново.", "Novāktos augus iestāda no jauna."),
    "nemos_enchantments:snow_walker": ("Allows you to walk over powder snow.", "Позволяет ходить по рыхлому снегу.", "Ļauj staigāt pa irdeno sniegu."),
    "nemos_enchantments:soul_binding": ("Soul-bound items stay with you after death.", "Привязанные к душе вещи остаются у тебя после смерти.", "Dvēselei piesaistītas mantas pēc nāves paliek pie tevis."),
    "nemos_enchantments:soul_touch": ("Lets you pick up a monster spawner.", "Позволяет забрать спавнер монстров.", "Ļauj paņemt monstru radītāju."),
    "nemos_enchantments:sprinter": ("Increases your movement speed.", "Увеличивает скорость передвижения.", "Palielina pārvietošanās ātrumu."),
    "nemos_enchantments:wisdom": ("Blocks and mobs drop 50% more experience per level.", "Блоки и мобы дают на 50% больше опыта за уровень.", "Bloki un mobi par katru līmeni dod par 50% vairāk pieredzes."),
    "runeforged:fire": ("Adds Fire damage: burns the target over time and lowers its Frost and Physical Resistance.",
                        "Добавляет урон огнём: поджигает цель и снижает её сопротивление холоду и физическому урону.",
                        "Pievieno uguns bojājumu: dedzina mērķi un samazina tā sala un fizisko pretestību."),
    "runeforged:holy": ("Adds Holy damage: stronger against undead, with a chance to restore your health.",
                        "Добавляет святой урон: сильнее против нежити и может восстановить здоровье.",
                        "Pievieno svēto bojājumu: spēcīgāks pret nemirušajiem un var atjaunot veselību."),
    "runeforged:lightning": ("Adds Lightning damage: a chance to call down a lightning strike for extra damage.",
                             "Добавляет урон молнией: может вызвать удар молнии с дополнительным уроном.",
                             "Pievieno zibens bojājumu: iespēja izsaukt zibens spērienu ar papildu bojājumu."),
    "runeforged:low_temperature": ("Adds Frost damage: chills and slows the target, and can stop Creeper explosions.",
                                   "Добавляет урон холодом: замедляет цель и может остановить взрыв крипера.",
                                   "Pievieno sala bojājumu: atvēsina un palēnina mērķi, var apturēt kripera sprādzienu."),
}
files = {}
for enchantment, texts in D.items():
    namespace, path = enchantment.split(":")
    for language, text in zip(("en_us", "ru_ru", "lv_lv"), texts):
        files.setdefault((namespace, language), {})[f"enchantment.{namespace}.{path}.desc"] = text
for (namespace, language), entries in files.items():
    target = Path("assets") / namespace / "lang" / f"{language}.json"
    target.parent.mkdir(parents=True, exist_ok=True)
    text = json.dumps(entries, ensure_ascii=False, indent=2) + "\n"
    assert "—" not in text
    target.write_text(text, encoding="utf-8")
print(len(D), "enchantments described in", len(files), "language files")
