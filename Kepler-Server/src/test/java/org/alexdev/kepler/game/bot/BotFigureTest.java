package org.alexdev.kepler.game.bot;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Legacy bot figures from tools/kepler.sql converted to v14, so every bot is sent as a user and the
 * infostand draws its avatar.
 */
class BotFigureTest {
    /** Sets never to send: invisible shirts in small rooms, hd model 2 (no small head), 32-bit hr 457. */
    private static final Set<Integer> UNUSABLE = Set.of(806, 807, 808, 809, 822, 823, 176, 580, 180, 190, 205, 207, 600, 615, 625, 627);

    /** name, legacy figure (kepler.sql), expected v14 figure, sex. */
    private static final String[][] KEPLER_SQL_BOTS = {
            {"Xenia", "sd=001&sh=002/54,178,190&lg=200/230,49,57&ch=506/230,49,57,141&lh=001/168,123,67&rh=001/168,123,67&hd=001/168,123,67&ey=001&fc=001/168,123,67&hr=506/194,26,134,190&hrb=506/2,3,4&rs=002/230,49,57&ls=002/230,49,57&bd=001/168,123,67", "8560760511827017300381847", "F"},
            {"Pamela", "sd=001&sh=002/148,98,32&lg=005/230,49,57&ch=201/255,255,255&lh=001/215,175,125&rh=001/215,175,125&hd=001/215,175,125&ey=001&fc=001/215,175,125&hr=507/103,78,59&rs=002/255,255,255&ls=002/255,255,255&bd=001/215,175,125", "8350860509715117300567525", "F"},
            {"Regina", "sd=001&sh=002/255,115,131&lg=005/255,115,131&ch=018/255,255,255&lh=001/255,204,153&rh=001/255,204,153&hd=001/255,204,153&ey=001&fc=001/255,204,153&hr=501/225,204,120&rs=003/255,255,255&ls=003/255,255,255&bd=001/255,204,153", "8320260501715197300768509", "F"},
            {"James", "sd=001&sh=001/17,17,17&lg=001/17,17,17&ch=800&lh=001/240,213,179&rh=001/240,213,179&hd=001/240,213,179&ey=001/254,202,150&fc=001/240,213,179&hr=201/17,17,17&rs=800&ls=800&bd=001/240,213,179", "8301618506270152900226001", "M"},
            {"Marion", "sd=001&sh=002/17,17,17&lg=005/255,115,131&ch=018/17,17,17&lh=001/230,200,162&rh=001/230,200,162&hd=001/230,200,162&ey=002&fc=001/230,200,162&hr=202/165,90,24&rs=003/17,17,17&ls=003/17,17,17&bd=001/230,200,162", "8380960507715197301068503", "F"},
            {"Brone", "sd=001/0&hr=008/115,99,70&hd=002/145,98,55&ey=005/0&fc=001/145,98,55&bd=001/145,98,55&lh=001/145,98,55&rh=001/145,98,55&ch=005/17,17,17&ls=002/17,17,17&rs=002/17,17,17&lg=004/17,17,17&sh=003/17,17,17", "1251718518280153000623507", "M"},
            {"Marcus", "sd=001/0&hr=010/224,186,120&hd=002/255,204,153&ey=005/0&fc=001/255,204,153&bd=001/255,204,153&lh=001/255,204,153&rh=001/255,204,153&ch=005/59,122,192&ls=002/59,122,192&rs=002/59,122,192&lg=006/119,159,187&sh=001/223,175,209", "1550118501285012900523524", "M"},
            {"Dave", "sd=001/0&hr=995/255,255,255&hd=001/255,204,153&ey=001/0&fc=001/255,204,153&bd=001/255,204,153&lh=001/255,204,153&rh=001/255,204,153&ch=995/255,255,255&ls=002/255,255,255&rs=002/255,255,255&lg=999/255,255,255&sh=001/121,94,83", "8310818501275012900221025", "M"},
            {"Sadie", "sd=001&sh=001/36&lg=999/255,255,255&ch=006/163&lh=001/255,204,153&rh=001/255,204,153&hd=001/255,204,153&ey=001&fc=001/255,204,153&hr=006/250,230,150&rs=002/163&ls=002/163&bd=001/255,204,153", "5150560501715207250965020", "F"},
            {"Reginaldo", "sd=001&hd=001/236,214,186&fc=001/236,214,186&bd=001/236,214,186&rh=001/236,214,186&lh=001/236,214,186&hr=001/255,255,255&lg=001/40,40,40&sh=001/150,0,0&rs=002/255,255,255&ls=002/255,255,255&ch=202/255,255,255", "1000118512270152900221525", "M"},
            {"Billy", "sd=001/0&hr=010/224,186,120&hd=002/255,203,152&ey=001/0&fc=001/255,203,152&bd=001/255,203,152&lh=001/255,203,152&rh=001/255,203,152&ch=502/57,65,148&ls=001/57,65,148&rs=001/57,65,148&lg=006/102,102,102&sh=003/51,51,51", "1550118501285143000680538", "M"},
            {"Phillip", "sd=001/0&hr=010/255,255,255&hd=002/255,204,153&ey=001/0&fc=001/255,204,153&bd=001/255,204,153&lh=001/255,204,153&rh=001/255,204,153&ch=001/217,113,69&ls=002/217,113,69&rs=002/217,113,69&lg=001/102,102,102&sh=003/47,45,38", "8510718501270143000621512", "M"},
            {"Ariel", "sd=001&sh=001/36&lg=001/200,0,0&ch=006/163&lh=001/255,203,152&rh=001/255,203,152&hd=001/255,203,152&ey=001&fc=001/255,203,152&hr=003/250,50,2&rs=002/163&ls=002/163&bd=001/255,203,152", "8620360501695017250965020", "F"},
            {"Piers", "sd=001/0&hr=799/255,255,255&hd=002/255,204,153&ey=001/0&fc=001/255,204,153&bd=001/255,204,153&lh=001/255,204,153&rh=001/255,204,153&ch=003/255,255,255&ls=001/255,255,255&rs=001/255,255,255&lg=004/255,255,255&sh=004/255,255,255", "8310818501280203050122025", "M"},
            {"Marcel", "sd=001&sh=003/154,154,154&lg=001/98,90,32&ch=202/255,210,179&lh=001/255,210,179&rh=001/255,210,179&hd=001/255,204,153&ey=001&fc=001/255,204,153&hr=203/98,98,98&rs=001/255,210,179&ls=001/255,210,179&bd=001/255,204,153", "8440918501270113000421014", "M"},
            {"Chloe", "sd=001&sh=001/255,115,41&lg=999/255,255,255&ch=006/35,134,182&lh=001/255,203,1522&rh=001/255,203,152&hd=001/255,203,152&ey=001&fc=001/255,203,152&hr=003/250,50,2&rs=002/35,134,182&ls=002/35,134,182&bd=001/255,203,152", "8620360501715207250865020", "F"},
            {"Berith", "sd=001&sh=002/148,98,32&lg=005/230,49,57&ch=201/255,255,255&lh=001/215,175,125&rh=001/215,175,125&hd=001/215,175,125&ey=001&fc=001/215,175,125&hr=506/103,78,59&rs=002/255,255,255&ls=002/255,255,255&bd=001/215,175,125", "8340860509715117300567525", "F"},
            {"DJ von Beathoven", "sd=001&sh=001/36&lg=001&ch=005/163&lh=001/171,122,89&rh=001/171,122,89&hd=001/171,122,89&ey=001&fc=001/171,122,89&hr=931/255,255,255&rs=002/163&ls=002/163&bd=001/8", "8310818511270162900923524", "M"},
            {"Ray", "sd=001&hd=001/236,214,186&fc=001/236,214,186&bd=001/236,214,186&rh=001/236,214,186&lh=001/236,214,186&hr=010/177,139,86&lg=001/103,70,54&sh=001/30,30,30&rs=002/255,255,255&ls=002/255,255,255&ch=700/255,255,255look=2,2", "1550818512270112900221025", "M"},
            {"Amber", "sd=001&sh=002/148,98,32&lg=200/120,66,21&ch=018/255,230,57&lh=001/215,175,125&rh=001/215,175,125&hd=001/215,175,125&ey=001&fc=001/215,175,125&hr=023/255,230,50&rs=003/255,230,57&ls=003/255,230,57&bd=001/215,175,125", "5751460509827297300568504", "F"},
            {"Maarit", "sd=001&sh=001/255,115,41&lg=003/0,0,0&ch=018/0,0,0&lh=001/255,203,1522&rh=001/255,203,152&hd=001/255,203,152&ey=001&fc=001/255,203,152&hr=507/225,204,120&rs=001/255,255,255&ls=001/255,255,255&bd=001/255,255,255", "8350260501705037250868503", "F"},
            {"Mr. DJ", "sd=001&sh=001/255,255,255&lg=006/255,255,255&ch=003/255,255,255&lh=001/145,98,55&rh=001/145,98,55&hd=001/145,98,55&ey=001&fc=001/145,98,55&hr=008/145,98,55&rs=002/255,255,255&ls=002/255,255,255&bd=001/8", "1251718518285162900922525", "M"},
            {"Skye", "sd=001&sh=002/148,98,32&lg=003/84,98,139&ch=022/97,114,164&lh=001/215,175,125&rh=001/215,175,125&hd=001/215,175,125&ey=001&fc=001/215,175,125&hr=503/235,240,163&rs=002/97,114,164&ls=002/97,114,164&bd=001/215,175,125", "8330160509705017300588018", "F"},
            {"Jem", "sd=001&sh=002/255,115,131&lg=005/255,115,131&ch=015/255,189,189&lh=001/230,200,162&rh=001/230,200,162&hd=001/230,200,162&ey=002&fc=001/230,200,162&hr=501/165,90,24&hrb=501/2,3,4&rs=003/230,200,162&ls=003/230,200,162&bd=001/230,200,162", "8320960507715197300766518", "F"},
            {"Luigi", "sd=001&sh=001/255,0,0&lg=001/255,255,255&ch=995/255,255,255&lh=001/254,202,1508&rh=001/254,202,1508&hd=001/254,202,150&ey=001/254,202,150&fc=001/254,202,1508&hr=802/255,255,255&rs=002/255,255,255&ls=002/255,255,255&bd=001/254,202,150", "8310818501270162900221025", "M"},
            {"Mario", "sd=001&sh=001/255,0,0&lg=005/165,165,165&ch=017/255,255,255&lh=001/254,202,1508&rh=001/254,202,1508&hd=001/254,202,150&ey=001/254,202,150&fc=001/254,202,1508&hr=802/255,255,255&rs=002/255,255,255&ls=002/255,255,255&bd=001/254,202,150", "8310818501275262900225025", "M"},
            {"Ingemar", "sd=001&sh=001/255,255,255&lg=006/255,255,255&ch=001/255,255,255&lh=001/145,98,55&rh=001/145,98,55&hd=001/215,175,125&ey=001&fc=001/145,98,55&ha=10/255,255,255&rs=001/255,255,255&ls=001/255,255,255&bd=001/255,203,152", "8310818509285162900921025", "M"},
            {"Lofar", "sd=001&sh=001/194,227,232&lg=001/255,255,255&ch=001/255,255,255&lh=001/240,213,179&rh=001/240,213,179&hd=001/240,213,179&ey=001/254,202,150&fc=001/240,213,179&hr=888/255,255,255&rs=001/255,255,255&ls=001/255,255,255&bd=001/240,213,179", "8310818506270162900121025", "M"},
            {"Eric", "sd=001&sh=001/36&lg=201&ch=005/163,20,20&lh=001/171,122,89&rh=001/171,122,89&hd=001/171,122,89&ey=001&fc=001/171,122,89&hr=014/255,255,255&rs=002/163,20,20&ls=002/163,20,20&bd=001/8 look=2,2", "1400118511281012900923508", "M"},
            {"Maya", "sd=001&sh=002/148,98,32&lg=005/230,49,57&ch=911/255,255,255&lh=001/215,175,125&rh=001/215,175,125&hd=001/215,175,125&ey=001&fc=001/215,175,125&hr=017/103,78,59&rs=002/255,255,255&ls=002/255,255,255&bd=001/215,175,125", "5451060509715117300563025", "F"},
            {"ScubaJoe", "sd=001&hd=001/201,143,113&fc=001/201,143,113&bd=001/201,143,113&rh=001/201,143,113&lh=001/201,143,113&hr=504/223,218,190&lg=201/230,49,57&sh=002/246,172,49&rs=003/201,143,113&ls=003/201,143,113&ch=501/246,172,49look=2,2", "8291718521281022950380307", "M"},
            {"Tao", "sd=001&sh=001/36&lg=001&ch=002/163,20,20&lh=001/171,122,89&rh=001/171,122,89&hd=001/171,122,89&ey=001&fc=001/171,122,89&hr=791/255,255,255&rs=001/163,20,20&ls=001/163,20,20&bd=001/8", "5000460511695207250963007", "F"},
            {"Harry", "sd=001&sh=003/41,41,41&lg=006/51,51,51&ch=202/139,24,32&lh=001/255,210,179&rh=001/255,210,179&hd=001/255,204,153&ey=001&fc=001/255,204,153&hr=203/103,78,59&hrb=203/2,3,4&rs=001/255,255,255&ls=001/255,255,255&bd=001/255,204,153", "8310818501285153000621022", "M"},
            {"Miho", "sd=001&sh=001/36&lg=200/204,204,204&ch=204/204,204,204&lh=001/215,175,125&rh=001/215,175,125&hd=001/215,175,125&ey=001&fc=001/215,175,125&hr=504/50,91,106&rs=002/204,204,204&ls=002/204,204,204&bd=001/8", "8720560509827467250981546", "F"}
    };

    static Stream<String[]> keplerSqlBots() {
        return Arrays.stream(KEPLER_SQL_BOTS);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("keplerSqlBots")
    void everyKeplerSqlBotBecomesACompleteV14Figure(String name, String legacy, String expected, String sex) {
        String figure = BotFigure.normalise(legacy, name);

        assertThat(figure).isEqualTo(expected);
        assertThat(BotFigure.isV14(figure)).isTrue();
        assertThat(BotFigure.sexOf(figure)).isEqualTo(sex);
        assertThat(BotFigure.isComplete(figure, sex)).as("usable hr, hd, lg, sh and ch of one sex").isTrue();

        for (int i = 0; i < 25; i += 5) {
            assertThat(UNUSABLE).doesNotContain(Integer.parseInt(figure.substring(i, i + 3)));
        }
    }

    @Test
    void harryKeepsHisLookWithAShirtThatDrawsInTheWelcomeLounge() {
        // hr=203 brown -> 831 (hr 203) colour 8 (8A4924); hd=001 -> 185 (hd 1, eyes 1);
        // lg=006 -> 285 (lg 6); sh=003 dark grey -> 300 colour 6 (2F2D26);
        // ch=202 (invisible in small size) -> closest bitmap ch 1 with sleeves 1 -> 210, red BB2430.
        String figure = BotFigure.normalise(KEPLER_SQL_BOTS[32][1], "Harry");

        assertThat(KEPLER_SQL_BOTS[32][0]).isEqualTo("Harry");
        assertThat(figure).isEqualTo("8310818501285153000621022");
    }

    @Test
    void femaleLegacyFiguresAreFemale() {
        for (String name : new String[]{"Xenia", "Pamela", "Regina", "Marion", "Sadie", "Ariel", "Chloe", "Berith",
                "Amber", "Maarit", "Skye", "Jem", "Maya", "Miho"}) {
            String legacy = Arrays.stream(KEPLER_SQL_BOTS).filter(row -> row[0].equals(name)).findFirst().orElseThrow()[1];
            assertThat(BotFigure.sexOf(BotFigure.normalise(legacy, name))).as(name).isEqualTo("F");
        }
    }

    @Test
    void marioIsForcedMaleByName() {
        String legacy = Arrays.stream(KEPLER_SQL_BOTS).filter(row -> row[0].equals("Mario")).findFirst().orElseThrow()[1];

        assertThat(BotFigure.sexOf(BotFigure.normalise(legacy, null))).isEqualTo("F");
        assertThat(BotFigure.sexOf(BotFigure.normalise(legacy, "Mario"))).isEqualTo("M");
    }

    @Test
    void costumeHairFallsBackToDefaultHairButClothesKeepTheirColour() {
        // Luigi: hr=802 (chef hat, not in v14) and ch=995 white uniform.
        String figure = BotFigure.normalise("sd=001&sh=001/255,0,0&lg=001/255,255,255&ch=995/255,255,255"
                + "&hd=001/254,202,150&bd=001/254,202,150&hr=802/255,255,255", "Luigi");

        assertThat(figure.substring(0, 5)).isEqualTo(BotFigure.DEFAULT_MALE.substring(0, 5));
        assertThat(figure.substring(20)).isEqualTo("21025"); // default shirt set 210, colour 25 = EEEEEE
    }

    @Test
    void fallbacksAreCompleteForTheirSex() {
        assertThat(BotFigure.isComplete(BotFigure.DEFAULT_MALE, "M")).isTrue();
        assertThat(BotFigure.isComplete(BotFigure.DEFAULT_FEMALE, "F")).isTrue();
        assertThat(BotFigure.sexOf(BotFigure.DEFAULT_FEMALE)).isEqualTo("F");
    }

    @Test
    void unreadableFiguresUseTheFallback() {
        assertThat(BotFigure.normalise(null)).isEqualTo(BotFigure.DEFAULT_MALE);
        assertThat(BotFigure.normalise("")).isEqualTo(BotFigure.DEFAULT_MALE);
        assertThat(BotFigure.normalise("hd-180-1.ch-210-66.lg-270-82.sh-290-80")).isEqualTo(BotFigure.DEFAULT_MALE);
        assertThat(BotFigure.normalise("1000118001")).isEqualTo(BotFigure.DEFAULT_MALE);
        assertThat(BotFigure.normalise("ch=001/1,2,3&lg=001")).isEqualTo(BotFigure.DEFAULT_MALE);
    }

    @Test
    void legacyFigureWithoutShirtGetsTheDefaultShirt() {
        String figure = BotFigure.normalise("hd=001/255,204,153&bd=001/255,204,153&hr=010/224,186,120");

        assertThat(BotFigure.isComplete(figure, "M")).isTrue();
        assertThat(figure.substring(20)).isEqualTo(BotFigure.DEFAULT_MALE.substring(20));
    }

    @Test
    void validV14FigureIsKept() {
        assertThat(BotFigure.normalise(" 8310818501275343000621022 ")).isEqualTo("8310818501275343000621022");
        assertThat(BotFigure.normalise(BotFigure.DEFAULT_FEMALE)).isEqualTo(BotFigure.DEFAULT_FEMALE);
    }

    @Test
    void v14InvisibleShirtAndSmallMissingHeadAreReplaced() {
        // 180 = hd model 2 (no small bitmap) -> 185 same eyes; 808 = ch 202 (invisible small) -> ch 1 sleeves 2.
        String figure = BotFigure.normalise("8310818001275343000680803");

        assertThat(figure).startsWith("8310818501275343000");
        assertThat(figure.substring(20, 23)).isEqualTo("215");
        assertThat(BotFigure.isComplete(figure, "M")).isTrue();
    }

    @Test
    void legacyColoursFollowTheClient() {
        assertThat(BotFigure.legacyColour("139,24,32")).isEqualTo(0x8B1820);
        assertThat(BotFigure.legacyColour("255,255,255")).isEqualTo(0xEEEEEE);
        assertThat(BotFigure.legacyColour("")).isEqualTo(0xEEEEEE);
        assertThat(BotFigure.legacyColour("0")).isEqualTo(0xEEEEEE);
        assertThat(BotFigure.legacyColour("230,49,57,141")).isEqualTo(0xEEEEEE);
        assertThat(BotFigure.legacyColour("8")).isEqualTo(0xFFCC99);   // skin in #systemMac
        assertThat(BotFigure.legacyColour("163")).isEqualTo(0x3366CC); // blue
        assertThat(BotFigure.legacyColour("255")).isEqualTo(0x000000);
    }

    @Test
    void legacyFiguresWithSpacedColoursOrNoBodyPartAreStillConverted() {
        String spaced = BotFigure.normalise("sd=001&hr=203/103, 78, 59&hd=001/255, 204, 153&ey=001&fc=001/255,204,153&lg=001/40,40,40&sh=001/30,30,30&ch=202/139,24,32", "Harry");
        String noBody = BotFigure.normalise("hr=203/103,78,59&hd=001/255,204,153&ch=202/139,24,32", "Harry");

        assertThat(spaced).matches("\\d{25}");
        assertThat(noBody).matches("\\d{25}");
        assertThat(spaced.substring(0, 3)).isEqualTo(noBody.substring(0, 3));
    }
}
