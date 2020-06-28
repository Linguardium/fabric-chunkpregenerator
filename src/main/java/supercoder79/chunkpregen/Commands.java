package supercoder79.chunkpregen;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.fabricmc.fabric.api.command.v1.CommandRegistrationCallback;
import net.fabricmc.fabric.api.registry.CommandRegistry;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.LiteralText;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;

import java.text.DecimalFormat;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Commands {
	private static DecimalFormat format = new DecimalFormat("#.00");
	private static int threadsDone = 0;
	private static ConcurrentLinkedQueue<ChunkPos> queue = new ConcurrentLinkedQueue<>();
	private static int total = 1;
	private static boolean shouldGenerate = false;
	private static ExecutorService executor;

	public static void init() {
        executor = Executors.newCachedThreadPool();
		CommandRegistrationCallback.EVENT.register((dispatcher, dedicated) -> {
			LiteralArgumentBuilder<ServerCommandSource> lab = CommandManager.literal("pregen")
					.requires(executor -> executor.hasPermissionLevel(2));

			lab.then(CommandManager.literal("start")
					.then(CommandManager.argument("radius", IntegerArgumentType.integer(0))
					.then(CommandManager.argument("first", IntegerArgumentType.integer(0))
							.executes(cmd -> {
				if (!shouldGenerate) {
					shouldGenerate = true;

					ServerCommandSource source = cmd.getSource();

					int radius = IntegerArgumentType.getInteger(cmd, "radius");
					int first = IntegerArgumentType.getInteger(cmd, "first");

					ChunkPos pos;
					if (source.getEntity() == null) {
						pos = new ChunkPos(0, 0);
					} else {
						pos = new ChunkPos(new BlockPos(source.getPlayer().getPos()));
					}

					queue.clear();

					total = 0;
					// use a Set to prevent duplicating corners...not sure if this is faster than just leaving them in queue
					Set<ChunkPos> chunks = new HashSet<>();
					// concentric squares. start in the middle or at "first" ring
					for (int ring = first; ring < radius; ring++) {
						for (int v = -ring; v <= ring; v++;) {
							chunks.add(new ChunkPos(pos.x+ring,pox.z+v))
							chunks.add(new ChunkPos(pos.x-ring,pox.z+v))
							chunks.add(new ChunkPos(pos.x+v,pox.z+ring))
							chunks.add(new ChunkPos(pos.x+v,pox.z-ring))
						}
					}
					total = chunks.size(); // why count as they add when you can just count it all at the end
					queue.addAll(chunks); // queue up the set

					execute(source);

					source.sendFeedback(new LiteralText("Pregenerating " + total + " chunks..."), true);
				} else {
					cmd.getSource().sendFeedback(new LiteralText("Pregen already running. Please execute '/pregen stop' to start another pregeneration."), true);
				}
				return 1;
			})));

			lab.then(CommandManager.literal("stop")
					.executes(cmd -> {
				if (shouldGenerate) {
					int amount = total-queue.size();
					int ring = (int)Math.floor(total/8.0f);
					cmd.getSource().sendFeedback(new LiteralText("Pregen stopped! " + (amount) + " out of " + total + " generated. (" + (((double)(amount) / (double)(total))) * 100 + "%)"), true);
					cmd.getSource().sendFeedback(new LiteralText("Last completed radius: " + (ring)"), true);
				}
				shouldGenerate = false;
				return 1;
			}));

			lab.then(CommandManager.literal("status")
					.executes(cmd -> {
				if (shouldGenerate) {
					int amount = total-queue.size();
					int ring = (int)Math.floor(total/8.0f);
					cmd.getSource().sendFeedback(new LiteralText("Pregen status: " + (amount) + " out of " + total + " generated. (" + (((double)(amount) / (double)(total))) * 100 + "%)"), true);
					cmd.getSource().sendFeedback(new LiteralText("Last completed radius: " + (ring)"), true);
				} else {
					cmd.getSource().sendFeedback(new LiteralText("No pregeneration currently running. Run /pregen start <radius> to start."), false);
				}
				return 1;
			}));

			lab.then(CommandManager.literal("help").
					executes(cmd -> {
				ServerCommandSource source = cmd.getSource();

				source.sendFeedback(new LiteralText("/pregen start <radius> <start_at> - Pregenerate a square centered on the player that is <radius> chunks from the player in each direction and starts at <start_at> chunks away."), false);
				source.sendFeedback(new LiteralText("/pregen stop - Stop pregeneration and displays the amount completed."), false);
				source.sendFeedback(new LiteralText("/pregen status - Display the amount of chunks pregenerated."), false);
				source.sendFeedback(new LiteralText("/pregen help - Display this message."), false);
				return 1;
			}));

			dispatcher.register(lab);
		});
	}

	private static void incrementAmount(ServerCommandSource source) {
		int amount = total - queue.size();

		if (amount % 100 == 0) {
			System.gc();
			source.sendFeedback(new LiteralText("Pregenerated " + (format.format(((double)(amount) / (double)(total)) * 100)) + "%"), true);
		}
	}

	private static void finishThread(ServerCommandSource source) {
		threadsDone++;
		if (threadsDone == 4) {
			threadsDone = 0;
			source.sendFeedback(new LiteralText("Pregeneration Done!"), true);
			shouldGenerate = false;
		}
	}

    private static void execute(ServerCommandSource source) {
        threadsDone = 0;

        for (int i = 0; i < 4; i++) {
            executor.submit(new ChunkWorker(source));
        }
    }

	static class ChunkWorker implements Runnable {
		private ServerCommandSource source;

		ChunkWorker(ServerCommandSource source) {
			this.source = source;
		}

		@Override
		public void run() {
			ServerWorld world = source.getWorld();

			while (shouldGenerate) {
				ChunkPos pos = queue.poll();
				if (pos == null) break;

				world.getChunk(pos.x, pos.z);
				incrementAmount(source);
			}

			finishThread(source);
		}
	}
}
