"""Clean the course-provided slang.txt dictionary.

Usage: python tools/fix_slang.py <input> <output>

Line format: SLANG`Meaning 1| Meaning 2  (first line is the header "Slag`Meaning").
Every correction is listed in the tables below so it can be reviewed and
re-checked by tools/verify_slang.py.
"""
import sys
from collections import OrderedDict

HEADER = "Slag`Meaning"  # kept as in the original course file

# Keys that are wrong in the source file.
KEY_FIXES = {
    "Æ": "®",            # "Æ" -> "®"  (cp1252 0xAE misread as Mac Roman)
    "ô": "™",            # "ô" -> "™"  (cp1252 0x99 misread as Mac Roman)
    "ALL- NIGHTER": "ALL-NIGHTER",
}

# (key, wrong meaning, correct meaning) -- each must match exactly once.
MEANING_FIXES = [
    # Meanings truncated at the first non-ASCII character
    ("ELNS", "Eller noe s", "Eller noe sånt"),
    ("EVT", "Ei Voi Tiet", "Ei Voi Tietää"),
    ("GMBH", "Gesellschaft mit Beschr", "Gesellschaft mit Beschränkter Haftung"),
    ("ILM", "I lige m", "I lige måde"),
    # Misc
    ("HIR", "HIS\\HER", "His/Her"),
    ("YMCA", "Young Mens Christian Association", "Young Men's Christian Association"),
    # Spelling
    ("A7A", "Frusration, anger", "Frustration, anger"),
    ("AGGY", "Aggitated", "Agitated"),
    ("AIM", "AOL Instant Messanger", "AOL Instant Messenger"),
    ("AIMBOT", "Trageting script in games", "Targeting script in games"),
    ("AMC", "American Motors Corportation", "American Motors Corporation"),
    ("AVO", "Afternooon", "Afternoon"),
    ("BANGING", "Very beautifu", "Very beautiful"),
    ("BEANS", "Ecstacy", "Ecstasy"),
    ("BELLIG", "Belligerant", "Belligerent"),
    ("BLATES", "Blatently", "Blatantly"),
    ("BUB", "Champange", "Champagne"),
    ("CAMO", "Camoflauge", "Camouflage"),
    ("CRC", "Cyclic Redundency Check", "Cyclic Redundancy Check"),
    ("CUTE", "Someone/thing that is attrative in an innocent way",
     "Someone/thing that is attractive in an innocent way"),
    ("D:", "Suprised", "Surprised"),
    ("DCED", "Dissconected", "Disconnected"),
    ("ECO", "Enconomical", "Economical"),
    ("ESB", "Empire Stikes Back", "Empire Strikes Back"),
    ("FIT", "Good lokking", "Good looking"),
    ("FM", "Freqeuncy modulation", "Frequency modulation"),
    ("FOFO", "Fourty Four caliber gun", "Forty Four caliber gun"),
    ("FRESHIE", "Imigrant", "Immigrant"),
    ("FTSE", "Financial Times and the London Stock Exchang",
     "Financial Times and the London Stock Exchange"),
    ("FWD", "Foward", "Forward"),
    ("GC", "Good Contition", "Good Condition"),
    ("GOSH", "Exclamation of suprise or shock", "Exclamation of surprise or shock"),
    ("HOOPS", "Basketabll", "Basketball"),
    ("LED", "Light Emiting Diode", "Light Emitting Diode"),
    ("LUI", "Living Under the Infuence", "Living Under the Influence"),
    ("MCP", "Male Chauvanist Pig", "Male Chauvinist Pig"),
    ("MPAA", "Motion Picture Assosciation of America", "Motion Picture Association of America"),
    ("NOLA", "New Orleans, Louisana", "New Orleans, Louisiana"),
    ("OBHWF", "One Big Happy Weasely Family", "One Big Happy Weasley Family"),
    ("OEM", "Original Equipment Manufacurer", "Original Equipment Manufacturer"),
    ("OO", "Suprised", "Surprised"),
    ("PFT", "An exlamation of disbelief", "An exclamation of disbelief"),
    ("PHD", "Doctor of Philosphy", "Doctor of Philosophy"),
    ("POT", "Mariujana", "Marijuana"),
    ("POTC", "Pirates Of The Carribean", "Pirates Of The Caribbean"),
    ("POW", "Prisioner of War", "Prisoner of War"),
    ("PSA", "Publice Service Announcement", "Public Service Announcement"),
    ("PWOR", "Proceeding With Orders Recieved", "Proceeding With Orders Received"),
    ("QOS", "Quantam Of Solace", "Quantum Of Solace"),
    ("RCT", "Roller Coster Tycoon", "Roller Coaster Tycoon"),
    ("SCO", "San Fransisco", "San Francisco"),
    ("SKITZ", "Schitzophrenic", "Schizophrenic"),
    ("SONAR", "SOund Navagation And Ranging", "SOund Navigation And Ranging"),
    ("SPAM", "Unsolicted email", "Unsolicited email"),
    ("SPLIF", "A cigarette of tobacco and cannibis", "A cigarette of tobacco and cannabis"),
    ("SPOILER", "Something you would've prefered to find out yourself",
     "Something you would've preferred to find out yourself"),
    ("TARDIS", "Time And Relative Dimentions In Space", "Time And Relative Dimensions In Space"),
    ("TCOB", "Taking Care of Buisness", "Taking Care of Business"),
    ("TDS", "Technical Dificulties", "Technical Difficulties"),
    ("THIZZLE", "Esctasy tablet", "Ecstasy tablet"),
    ("TT", "Telegrahic Transfer", "Telegraphic Transfer"),
    ("VVV", "Refering to comment below", "Referring to comment below"),
    ("WACK", "Low quailty", "Low quality"),
    ("WOB", "Waste Of Bandwith", "Waste Of Bandwidth"),
    ("X)", "mischevious", "mischievous"),
    ("XC", "Cross Counrty", "Cross Country"),
    ("ZIP", "Ounce of marijauna", "Ounce of marijuana"),
]


def parse(text):
    """Return list of (key, [meanings]) in file order, header excluded.

    A line without a backtick is a continuation meaning of the previous entry.
    """
    lines = text.split("\r\n") if "\r\n" in text else text.split("\n")
    entries = []
    for line in lines[1:]:
        if not line.strip():
            continue
        if "`" not in line:
            if not entries:
                raise ValueError(f"orphan line with no previous entry: {line!r}")
            entries[-1][1].append(line.strip())
            continue
        key, value = line.split("`", 1)
        meanings = [m.strip() for m in value.split("|") if m.strip()]
        entries.append((key.strip(), meanings))
    return entries


def main(src, dst):
    with open(src, encoding="utf-8") as f:
        entries = parse(f.read())

    entries = [(KEY_FIXES.get(k, k), ms) for k, ms in entries]

    for key, old, new in MEANING_FIXES:
        hits = [ms for k, ms in entries if k == key and old in ms]
        if len(hits) != 1:
            raise SystemExit(f"fix ({key!r}, {old!r}) matched {len(hits)} entries")
        ms = hits[0]
        ms[ms.index(old)] = new

    # Merge duplicate keys, keeping the first position and unique meanings in order.
    merged = OrderedDict()
    for key, ms in entries:
        bucket = merged.setdefault(key, [])
        for m in ms:
            if m not in bucket:
                bucket.append(m)

    out = [HEADER] + [f"{k}`{'| '.join(ms)}" for k, ms in merged.items()]
    with open(dst, "w", encoding="utf-8", newline="") as f:
        f.write("\r\n".join(out) + "\r\n")
    print(f"entries read: {len(entries)}, unique slang words written: {len(merged)}")


if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2])
