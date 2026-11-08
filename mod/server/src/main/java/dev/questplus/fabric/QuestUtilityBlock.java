package dev.questplus.fabric;

import net.minecraft.core.*;
import net.minecraft.server.level.*;
import net.minecraft.util.RandomSource;
import net.minecraft.world.*;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.*;

public class QuestUtilityBlock extends Block implements EntityBlock {
    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final IntegerProperty SIZE = IntegerProperty.create("size",0,3);
    public static final IntegerProperty COLUMN = IntegerProperty.create("column",0,5);
    public static final IntegerProperty ROW = IntegerProperty.create("row",0,3);
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;
    public static final BooleanProperty MODULAR = BooleanProperty.create("modular");
    public static final IntegerProperty EDGES = IntegerProperty.create("edges",0,15);
    final int kind;
    private boolean removingBoard;
    QuestUtilityBlock(int kind, Properties properties) {
        super(properties); this.kind=kind;
        if(kind==0) registerDefaultState(stateDefinition.any().setValue(FACING,Direction.NORTH).setValue(SIZE,0).setValue(COLUMN,0).setValue(ROW,0).setValue(MODULAR,false).setValue(EDGES,15));
        else if(kind==1) registerDefaultState(stateDefinition.any().setValue(FACING,Direction.NORTH));
        else registerDefaultState(stateDefinition.any().setValue(POWERED,false));
    }
    static final class Board extends QuestUtilityBlock {
        Board(Properties p) { super(0,p); }
        @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> b) { b.add(FACING,SIZE,COLUMN,ROW,MODULAR,EDGES); }
    }
    static final class Station extends QuestUtilityBlock {
        Station(Properties p) { super(1,p); }
        @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> b) { b.add(FACING); }
    }
    static final class Relay extends QuestUtilityBlock {
        Relay(Properties p) { super(2,p); }
        @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> b) { b.add(POWERED); }
    }
    static BlockPos at(BlockPos origin, Direction facing, int x, int y) {
        return origin.relative(facing.getClockWise(), x).above(y);
    }
    static BlockPos master(BlockPos pos, BlockState state) {
        if(state.getValue(MODULAR))return pos;
        return at(pos,state.getValue(FACING),-state.getValue(COLUMN),-state.getValue(ROW));
    }
    static boolean primary(BlockState state) { return state.getValue(MODULAR)||state.getValue(COLUMN)==0 && state.getValue(ROW)==0; }
    static boolean room(Level level, BlockPos origin, Direction facing, int size, int oldSize) {
        for(int y=0;y<QuestWorldBlocks.HEIGHT[size];y++) for(int x=0;x<QuestWorldBlocks.WIDTH[size];x++) {
            var p=at(origin,facing,x,y);
            if(!level.hasChunkAt(p) || level.isOutsideBuildHeight(p) || !level.getWorldBorder().isWithinBounds(p)) return false;
            var s=level.getBlockState(p);
            boolean own=oldSize>=0 && s.is(QuestWorldBlocks.BOARD) && master(p,s).equals(origin);
            if(!own && !s.canBeReplaced()) return false;
            if(!level.getBlockState(p.relative(facing.getOpposite())).isFaceSturdy(level,p.relative(facing.getOpposite()),facing)) return false;
        }
        return true;
    }
    static void assemble(Level level, BlockPos origin, Direction facing, int size) {
        for(int y=0;y<QuestWorldBlocks.HEIGHT[size];y++) for(int x=0;x<QuestWorldBlocks.WIDTH[size];x++) {
            var p=at(origin,facing,x,y);
            level.setBlock(p,QuestWorldBlocks.BOARD.defaultBlockState().setValue(FACING,facing).setValue(SIZE,size)
                    .setValue(COLUMN,x).setValue(ROW,y),3);
            level.scheduleTick(p,QuestWorldBlocks.BOARD,40);
        }
    }
    @Override public BlockState getStateForPlacement(BlockPlaceContext c) {
        if(kind==2) return defaultBlockState();
        var s=defaultBlockState().setValue(FACING,c.getHorizontalDirection().getOpposite());
        if(kind==0) {
            var clicked=c.getLevel().getBlockState(c.getClickedPos().relative(c.getClickedFace().getOpposite()));
            Direction facing=clicked.is(this)?clicked.getValue(FACING):c.getClickedFace();
            if(!facing.getAxis().isHorizontal())return null;
            s=s.setValue(FACING,facing).setValue(MODULAR,true);
            var back=c.getClickedPos().relative(facing.getOpposite());
            if(!c.getLevel().getBlockState(back).isFaceSturdy(c.getLevel(),back,facing))return null;
            var joined=new java.util.HashSet<BlockPos>();
            for(var d:java.util.List.of(Direction.UP,Direction.DOWN,facing.getClockWise(),facing.getCounterClockWise())) {
                var p=c.getClickedPos().relative(d);
                if(c.getLevel().hasChunkAt(p)&&QuestBoardParts.modular(c.getLevel().getBlockState(p))
                        &&c.getLevel().getBlockState(p).getValue(FACING)==facing
                        &&c.getLevel().getBlockEntity(p) instanceof QuestUtilityEntity be
                        &&c.getPlayer()!=null&&c.getPlayer().getUUID().equals(be.owner))
                    for(var part:QuestBoardParts.connected(be))joined.add(part.getBlockPos());
            }
            if(joined.size()>=QuestBoardParts.LIMIT)return null;
        }
        return s;
    }
    @Override public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        if(!level.isClientSide()) {
            if(kind==0 && !state.getValue(MODULAR)) assemble(level,pos,state.getValue(FACING),state.getValue(SIZE));
            if(level.getBlockEntity(pos) instanceof QuestUtilityEntity be && placer!=null) be.claim(placer.getUUID(),stack);
            if(kind==0 && state.getValue(MODULAR) && level.getBlockEntity(pos) instanceof QuestUtilityEntity be) {
                for(var other:QuestBoardParts.connected(be))if(other!=be) {be.config=other.config;be.boundTeam=other.boundTeam;break;}
                QuestBoardParts.rebuild(be);level.scheduleTick(pos,this,40);
            }
        }
    }
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return kind==0 && !primary(state) ? null : new QuestUtilityEntity(pos,state);
    }
    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return !level.isClientSide() && type==QuestWorldBlocks.ENTITY ? (l,p,s,be)->((QuestUtilityEntity)be).serverTick() : null;
    }
    @Override protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        if(kind==2) return box(0,0,0,16,9,16);
        if(kind==1) return Shapes.block();
        return switch(state.getValue(FACING)) {
            case NORTH->box(0,0,10,16,16,16); case SOUTH->box(0,0,0,16,16,6);
            case EAST->box(0,0,0,6,16,16); default->box(10,0,0,16,16,16);
        };
    }
    @Override protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if(player instanceof ServerPlayer actor) {
            var p=kind==0 ? master(pos,state) : pos;
            if(kind==0 && state.getValue(MODULAR) && level.getBlockEntity(p) instanceof QuestUtilityEntity tile) {
                if(!level.hasChunkAt(tile.boardAnchor))QuestBoardParts.rebuild(tile);
                p=tile.boardAnchor;
            }
            if(level.getBlockEntity(p) instanceof QuestUtilityEntity be) QuestBlockNetwork.open(actor,be);
        }
        return InteractionResult.SUCCESS;
    }
    @Override protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
            InteractionHand hand, BlockHitResult hit) {
        if(kind==0 && stack.is(asItem()))return InteractionResult.PASS;
        if(!player.isShiftKeyDown() && kind==1 && player instanceof ServerPlayer actor
                && level.getBlockEntity(pos) instanceof QuestUtilityEntity be && be.insertHeld(actor,hand,hit.getDirection()))
            return InteractionResult.SUCCESS;
        return useWithoutItem(state,level,pos,player,hit);
    }
    @Override protected boolean isSignalSource(BlockState state) { return kind==2; }
    @Override public BlockState playerWillDestroy(Level level,BlockPos pos,BlockState state,Player player) {
        if(kind==0 && !state.getValue(MODULAR) && player.isCreative() && !level.isClientSide()) {
            var master=master(pos,state);
            if(!master.equals(pos) && level.hasChunkAt(master))level.destroyBlock(master,false);
        }
        return super.playerWillDestroy(level,pos,state,player);
    }
    @Override protected java.util.List<ItemStack> getDrops(BlockState state, net.minecraft.world.level.storage.loot.LootParams.Builder params) {
        if(kind==0 && !primary(state))return java.util.List.of();
        var drops=super.getDrops(state,params);
        if(kind==1 && !drops.isEmpty() && params.getOptionalParameter(net.minecraft.world.level.storage.loot.parameters.LootContextParams.BLOCK_ENTITY) instanceof QuestUtilityEntity be) {
            try { return java.util.List.of(be.packForMove()); }
            catch(java.io.IOException error) { QuestPlusMod.LOGGER.error("Retained station resources for recovery",error); }
        }
        return drops;
    }
    @Override protected int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return kind==2 && level.getBlockEntity(pos) instanceof QuestUtilityEntity be ? be.signal : 0;
    }
    @Override protected int getDirectSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return getSignal(state,level,pos,direction);
    }
    @Override protected boolean hasAnalogOutputSignal(BlockState state) { return kind==1 || kind==2; }
    @Override protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos, Direction direction) {
        return level.getBlockEntity(pos) instanceof QuestUtilityEntity be ? be.signal : 0;
    }
    @Override protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if(kind==2 && level.getBlockEntity(pos) instanceof QuestUtilityEntity be) { be.setSignal(0); return; }
        if(kind!=0) return;
        if(state.getValue(MODULAR)) {
            var back=pos.relative(state.getValue(FACING).getOpposite());
            if(level.hasChunkAt(back) && !level.getBlockState(back).isFaceSturdy(level,back,state.getValue(FACING)))level.destroyBlock(pos,true);
            else level.scheduleTick(pos,this,100);
            return;
        }
        var master=master(pos,state);
        if(level.hasChunkAt(master) && (!level.getBlockState(master).is(this)
                || !primary(level.getBlockState(master)))) { level.removeBlock(pos,false); return; }
        var back=pos.relative(state.getValue(FACING).getOpposite());
        if(!level.getBlockState(back).isFaceSturdy(level,back,state.getValue(FACING))) level.destroyBlock(master,true);
        else level.scheduleTick(pos,this,100);
    }
    @Override protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean moved) {
        super.affectNeighborsAfterRemoval(state,level,pos,moved);
        if(kind==2) level.updateNeighborsAt(pos,this);
        if(kind!=0 || level.getBlockState(pos).is(this)) return;
        if(state.getValue(MODULAR)) {QuestBoardParts.changedAround(level,pos,state.getValue(FACING));return;}
        if(removingBoard)return;
        var origin=master(pos,state);
        if(level.getBlockEntity(origin) instanceof QuestUtilityEntity be && be.resizing) return;
        int size=state.getValue(SIZE);
        removingBoard=true;
        try {
        if(!pos.equals(origin) && level.hasChunkAt(origin) && level.getBlockState(origin).is(this)) level.destroyBlock(origin,true);
        for(int y=0;y<QuestWorldBlocks.HEIGHT[size];y++) for(int x=0;x<QuestWorldBlocks.WIDTH[size];x++) {
            var p=at(origin,state.getValue(FACING),x,y);
            if(!p.equals(pos) && level.hasChunkAt(p) && level.getBlockState(p).is(this)
                    && master(p,level.getBlockState(p)).equals(origin)) level.removeBlock(p,false);
        }
        } finally {removingBoard=false;}
    }
}
