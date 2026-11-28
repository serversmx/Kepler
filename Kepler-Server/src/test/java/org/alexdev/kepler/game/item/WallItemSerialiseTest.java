package org.alexdev.kepler.game.item;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.alexdev.kepler.game.item.base.ItemBehaviour;
import org.alexdev.kepler.messages.outgoing.rooms.items.PLACE_WALLITEM;
import org.alexdev.kepler.server.netty.streams.NettyResponse;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Room_Handler_Class.handle_items reads one wall item per RETURN-separated line and splits it on TAB:
 * id, class, owner, location, type. Nothing inside a field may contain RETURN, LF or TAB.
 */
class WallItemSerialiseTest {

    private static Item wallItem(String sprite, String wallPosition, String customData, ItemBehaviour... behaviours) {
        Item item = new Item();
        item.setId(4321);
        item.getDefinition().setSprite(sprite);
        item.getDefinition().addBehaviour(ItemBehaviour.WALL_ITEM);

        for (ItemBehaviour behaviour : behaviours) {
            item.getDefinition().addBehaviour(behaviour);
        }

        item.setWallPosition(wallPosition);
        item.setCustomData(customData);
        return item;
    }

    private static String body(Item item) {
        PLACE_WALLITEM message = new PLACE_WALLITEM(item);
        ByteBuf buffer = Unpooled.buffer();
        message.compose(new NettyResponse(message.getHeader(), buffer));
        return buffer.toString(StandardCharsets.ISO_8859_1).substring(2); // skip B64 header
    }

    @Test
    void photoCaptionStaysOnTheItemLine() {
        String body = body(wallItem("photo", ":w=3,2 l=10,40 r", "27-11-2026 14:05\r¡Hola!\rHabboP", ItemBehaviour.PHOTO));

        assertThat(body).endsWith("\r");
        String line = body.substring(0, body.length() - 1);
        assertThat(line).doesNotContain("\r").doesNotContain("\n");

        String[] fields = line.split("\t", -1);
        assertThat(fields).hasSize(5);
        assertThat(fields[0]).isEqualTo("4321");
        assertThat(fields[1]).isEqualTo("photo");
        assertThat(fields[3]).isEqualTo(":w=3,2 l=10,40 r");
        assertThat(fields[4]).isEqualTo("27-11-2026 14:05 ¡Hola! HabboP");
    }

    @Test
    void wallPositionCannotInjectLinesOrFields() {
        String body = body(wallItem("poster", ":w=3,2 l=10,40 r\r99\tx", "5"));

        String line = body.substring(0, body.length() - 1);
        assertThat(line).doesNotContain("\r");
        assertThat(line.split("\t", -1)).hasSize(5);
    }

    @Test
    void postItOnlySendsItsColour() {
        String body = body(wallItem("post.it", ":w=1,1 l=2,2 l", "FFFF33Primera\rSegunda", ItemBehaviour.POST_IT));

        assertThat(body).isEqualTo("4321\tpost.it\t \t:w=1,1 l=2,2 l\tFFFF33\r");
    }

    @Test
    void shortPostItDataDoesNotThrow() {
        assertThat(body(wallItem("post.it", ":w=1,1 l=2,2 l", "FF", ItemBehaviour.POST_IT))).endsWith("\tFF\r");
    }

    @Test
    void plainPosterIsUnchanged() {
        assertThat(body(wallItem("poster", ":w=1,1 l=2,2 r", "12"))).isEqualTo("4321\tposter\t \t:w=1,1 l=2,2 r\t12\r");
    }
}
