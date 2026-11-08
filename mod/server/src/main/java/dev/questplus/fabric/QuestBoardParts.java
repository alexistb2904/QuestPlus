package dev.questplus.fabric;

import net.minecraft.core.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import java.util.*;

/** Connected modules keep their own settings so removing an anchor never loses a board. */
public final class QuestBoardParts {
    static final int LIMIT = 256;
    private QuestBoardParts() {}
    static boolean modular(BlockState s) { return s.is(QuestWorldBlocks.BOARD) && s.getValue(QuestUtilityBlock.MODULAR); }
    static List<QuestUtilityEntity> connected(QuestUtilityEntity start) {
        var level=start.getLevel(); var facing=start.getBlockState().getValue(QuestUtilityBlock.FACING);
        var found=new LinkedHashMap<BlockPos,QuestUtilityEntity>(); var pending=new ArrayDeque<QuestUtilityEntity>();
        found.put(start.getBlockPos(),start);pending.add(start);
        while(!pending.isEmpty()) {
            var be=pending.removeFirst();
            for(var d:List.of(Direction.UP,Direction.DOWN,facing.getClockWise(),facing.getCounterClockWise())) {
                var p=be.getBlockPos().relative(d);
                if(found.containsKey(p)||!level.hasChunkAt(p))continue;
                var s=level.getBlockState(p);
                if(!modular(s)||s.getValue(QuestUtilityBlock.FACING)!=facing)continue;
                if(level.getBlockEntity(p) instanceof QuestUtilityEntity next && Objects.equals(next.owner,start.owner)) {
                    found.put(p,next);pending.add(next);
                    if(found.size()>LIMIT)throw new IllegalArgumentException("Board exceeds 256 modules");
                }
            }
        }
        return List.copyOf(found.values());
    }
    static void rebuild(QuestUtilityEntity start) {
        var parts=connected(start); var level=start.getLevel();var facing=start.getBlockState().getValue(QuestUtilityBlock.FACING);
        var remaining=new HashMap<BlockPos,QuestUtilityEntity>();
        for(var be:parts)remaining.put(be.getBlockPos(),be);
        var positions=Set.copyOf(remaining.keySet());
        // Tile irregular outlines into rectangles: text never crosses a missing module.
        while(!remaining.isEmpty()) {
            var origin=remaining.keySet().stream().min(Comparator.comparingInt((BlockPos p)->p.getY())
                    .thenComparingInt(p->p.getX()*facing.getClockWise().getStepX()+p.getZ()*facing.getClockWise().getStepZ())).orElseThrow();
            int w=1,h=1;
            while(remaining.containsKey(QuestUtilityBlock.at(origin,facing,w,0)))w++;
            outer:while(true) {
                for(int x=0;x<w;x++)if(!remaining.containsKey(QuestUtilityBlock.at(origin,facing,x,h)))break outer;
                h++;
            }
            for(int y=0;y<h;y++)for(int x=0;x<w;x++) {
                var p=QuestUtilityBlock.at(origin,facing,x,y);var be=remaining.remove(p);
                be.boardWidth=x==0&&y==0?w:0;be.boardHeight=h;be.boardAnchor=origin;
                int edges=0;
                var directions=List.of(Direction.UP,Direction.DOWN,facing.getCounterClockWise(),facing.getClockWise());
                for(int i=0;i<4;i++) {
                    var neighbor=p.relative(directions.get(i));
                    if(!positions.contains(neighbor))edges|=1<<i;
                }
                level.setBlock(p,be.getBlockState().setValue(QuestUtilityBlock.EDGES,edges),2);
                be.setChanged();level.sendBlockUpdated(p,be.getBlockState(),be.getBlockState(),2);
                QuestWorldBlocks.enqueue(be);
            }
        }
    }
    static void validateTransfer(List<QuestUtilityEntity> parts,UUID owner) {
        var positions=new HashSet<BlockPos>();for(var part:parts)positions.add(part.getBlockPos());
        var level=parts.getFirst().getLevel();var facing=parts.getFirst().getBlockState().getValue(QuestUtilityBlock.FACING);
        for(var part:parts)for(var d:List.of(Direction.UP,Direction.DOWN,facing.getClockWise(),facing.getCounterClockWise())) {
            var p=part.getBlockPos().relative(d);
            if(positions.contains(p)||!level.hasChunkAt(p))continue;
            if(modular(level.getBlockState(p))&&level.getBlockState(p).getValue(QuestUtilityBlock.FACING)==facing
                    &&level.getBlockEntity(p) instanceof QuestUtilityEntity be&&Objects.equals(be.owner,owner))
                for(var next:connected(be))positions.add(next.getBlockPos());
            if(positions.size()>LIMIT)throw new IllegalArgumentException("Board exceeds 256 modules");
        }
    }
    static void changedAround(Level level,BlockPos pos,Direction facing) {
        var seen=new HashSet<BlockPos>();
        for(var d:List.of(Direction.UP,Direction.DOWN,facing.getClockWise(),facing.getCounterClockWise())) {
            var p=pos.relative(d);
            if(level.hasChunkAt(p)&&modular(level.getBlockState(p))&&level.getBlockEntity(p) instanceof QuestUtilityEntity be&&!seen.contains(p)) {
                for(var part:connected(be))seen.add(part.getBlockPos());
                rebuild(be);
            }
        }
    }
}
