"""Tune the bundled STIX derivative's radical roof; requires fontTools + brotli.

The generated WOFF2 is committed, so normal builds need no font tooling.
To regenerate, use --source with the original STIX Two Math 2.13 b171 WOFF2.
Use --check to validate the committed font without changing it.
"""
import argparse
from pathlib import Path

from fontTools.pens.recordingPen import RecordingPen
from fontTools.pens.t2CharStringPen import T2CharStringPen
from fontTools.ttLib import TTFont

TARGET = Path(__file__).resolve().parents[1] / "web/fonts/STIXTwoMath-Regular.woff2"
THICKNESS = 44
BEVEL = 11
CAPS = {"uni221A": 922, "uni221A.s1": 1589, "uni221A.s2": 2105,
        "uni221A.s3": 2626, "uni221A.t": 641}


def outline(font, name):
    pen = RecordingPen()
    font.getGlyphSet()[name].draw(pen)
    return pen.value


def validate(font):
    assert font["head"].unitsPerEm == 1000
    assert font["MATH"].table.MathConstants.RadicalRuleThickness.Value == THICKNESS
    for name, top in CAPS.items():
        points = [p for _, args in outline(font, name) for p in args]
        # Both the glyph's horizontal tip and the browser's extended rule
        # must have the same thickness, including assembled tall radicals.
        end = max(x for x, _ in points)
        assert sorted(y for x, y in points if x == end) == [top - THICKNESS, top], name
        assert max(y for _, y in points) == top, name
        # A small bevel removes the sharp upper corner at the surd/roof join.
        corner_x = min(x for x, y in points if y == top)
        assert (corner_x - BEVEL, top - BEVEL) in points, name


def tune(font):
    original = font["MATH"].table.MathConstants.RadicalRuleThickness.Value
    assert original == 68, "Expected the original STIX Two Math radical metrics"
    before = {name: outline(font, name) for name in font.getGlyphOrder()}
    cff = font["CFF "].cff
    top_dict = cff.topDictIndex[0]
    for name, top in CAPS.items():
        pen = T2CharStringPen(font["hmtx"][name][0], font.getGlyphSet())
        for operation, args in before[name]:
            if args:
                assert operation in ("moveTo", "lineTo")
                x, y = args[0]
                if y == top - original:
                    y = top - THICKNESS
                if name != "uni221A.t" and operation == "moveTo":
                    outer_x = x
                    y -= BEVEL
                elif name == "uni221A.t" and (x, y) == (849, top):
                    pen.lineTo((x + BEVEL, top))
                    y -= BEVEL
                getattr(pen, operation)((x, y))
            else:
                if name != "uni221A.t" and operation == "closePath":
                    pen.lineTo((outer_x + BEVEL, top))
                getattr(pen, operation)()
        previous = top_dict.CharStrings[name]
        top_dict.CharStrings[name] = pen.getCharString(previous.private, previous.globalSubrs)
    font["MATH"].table.MathConstants.RadicalRuleThickness.Value = THICKNESS
    # Keep the original copyright and OFL notices; identify the local derivative.
    names = {1: "SymvaCAS Math", 3: "SymvaCASMath-Regular;2.13b171;radical44",
             4: "SymvaCAS Math Regular", 6: "SymvaCASMath-Regular", 16: "SymvaCAS Math"}
    for record in font["name"].names:
        if record.nameID in names:
            record.string = names[record.nameID].encode(record.getEncoding())
    cff.fontNames[0] = "SymvaCASMath-Regular"
    top_dict.FamilyName = "SymvaCAS Math"
    top_dict.FullName = "SymvaCAS Math Regular"
    validate(font)
    for name in font.getGlyphOrder():
        if name not in CAPS:
            assert outline(font, name) == before[name], name


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source", type=Path, default=TARGET)
    parser.add_argument("--check", action="store_true")
    args = parser.parse_args()
    font = TTFont(args.source, recalcTimestamp=False)
    if args.check or font["MATH"].table.MathConstants.RadicalRuleThickness.Value == THICKNESS:
        validate(font)
    else:
        tune(font)
        font.save(TARGET)
        validate(TTFont(TARGET))
    print("Radical roof verified: 44/1000 em, matching glyph tips and beveled joins")


if __name__ == "__main__":
    main()
