package com.mertokan.omnilogistics;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.FunctionGameTestInstance;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestData;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * Since 1.21.5 a game test is a registered function plus a registered test instance, not an annotated method. The
 * test suites keep their plain static methods; this finds the ones marked {@link OmniTest} and registers each as
 * {@code omnilogistics:<suite>/<method>} on the "empty" structure.
 */
public final class GameTests {
    private GameTests() {}

    /** Marks a static {@code (GameTestHelper)} method as a game test. */
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    public @interface OmniTest {
        int timeoutTicks() default 100;
    }

    private static final List<Class<?>> SUITES = List.of(PipeGameTests.class, MachineGameTests.class, MinerGameTests.class,
        NbtRuleGameTests.class, EngineGameTests.class);
    private static final DeferredRegister<Consumer<GameTestHelper>> FUNCTIONS =
        DeferredRegister.create(Registries.TEST_FUNCTION, OmniLogistics.MODID);
    private record Entry(String name, int ticks) {}

    static void register(IEventBus bus) {
        List<Entry> entries = new ArrayList<>();
        for (Class<?> suite : SUITES)
            for (Method m : suite.getDeclaredMethods()) {
                OmniTest t = m.getAnnotation(OmniTest.class);
                if (t == null) continue;
                String name = (suite.getSimpleName().replace("GameTests", "") + "/" + m.getName()).toLowerCase(Locale.ROOT);
                FUNCTIONS.register(name, () -> h -> run(m, h));
                entries.add(new Entry(name, t.timeoutTicks()));
            }
        FUNCTIONS.register(bus);
        bus.addListener((RegisterGameTestsEvent e) -> {
            Holder<TestEnvironmentDefinition<?>> env = e.registerEnvironment(id("default"));
            for (Entry en : entries)
                e.registerTest(id(en.name()), new FunctionGameTestInstance(ResourceKey.create(Registries.TEST_FUNCTION, id(en.name())),
                    new TestData<>(env, id("empty"), en.ticks(), 0, true)));
        });
    }

    private static void run(Method m, GameTestHelper h) {
        try {
            m.invoke(null, h);
        } catch (InvocationTargetException x) {   // an assertion inside the test must reach the framework as itself
            if (x.getCause() instanceof RuntimeException r) throw r;
            throw new IllegalStateException(x.getCause());
        } catch (IllegalAccessException x) {
            throw new IllegalStateException(x);
        }
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(OmniLogistics.MODID, path);
    }

    /** The block entity at a test-relative position, as whatever the caller assigns it to. */
    @SuppressWarnings("unchecked")
    public static <T extends BlockEntity> T be(GameTestHelper h, BlockPos pos) {
        return (T) h.getLevel().getBlockEntity(h.absolutePos(pos));
    }

    /** The string-message assertions went away in 1.21.5; the tests keep their readable messages through this. */
    public static void check(GameTestHelper h, boolean ok, String message) {
        if (!ok) throw h.assertionException(Component.literal(message));
    }
}
