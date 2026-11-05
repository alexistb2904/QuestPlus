package dev.questplus.fabric;

import com.google.gson.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CommandRewardsTest {
    @Test void validatesTrustedCommandPayloadsWithoutEvaluatingThem(){
        assertDoesNotThrow(()->CommandRewards.validate(JsonParser.parseString(
            "{\"command\":\"say Quest completed\"}").getAsJsonObject()));
        for(var source:new String[]{
            "{\"command\":\"say\\ninvalid\"}",
            "{\"command\":\"\"}",
            "{\"command\":\"/op someone\",\"unexpected\":true}",
            "{\"other\":\"bad\"}"
        }) assertThrows(RuntimeException.class,()->
            CommandRewards.validate(JsonParser.parseString(source).getAsJsonObject()));
    }
}
