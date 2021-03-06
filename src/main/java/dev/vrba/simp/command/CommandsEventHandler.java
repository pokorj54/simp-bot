package dev.vrba.simp.command;

import dev.vrba.simp.exception.CommandExecutionException;
import discord4j.core.GatewayDiscordClient;
import discord4j.core.event.domain.message.MessageCreateEvent;
import org.jetbrains.annotations.NotNull;
import reactor.core.publisher.Mono;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class CommandsEventHandler {

    private final String prefix;

    private final CommandsRegistry registry;

    public CommandsEventHandler(@NotNull String prefix, @NotNull CommandsRegistry registry) {
        this.prefix = prefix;
        this.registry = registry;
    }

    public Mono<Void> register(@NotNull GatewayDiscordClient client) {
        return client.on(MessageCreateEvent.class)
                .filter(this::shouldHandle)
                .flatMap(this::handleMessage)
                .then();
    }

    private Mono<Void> handleMessage(@NotNull MessageCreateEvent event) {
        String content = event.getMessage().getContent();
        String name = content.replace(this.prefix, "").split("\s+")[0];

        return this.registry.findCommandByName(name)
                .map(command -> this.handleCommand(command, event))
                .orElse(Mono.empty());
    }

    private Mono<Void> handleCommand(@NotNull Command command, @NotNull MessageCreateEvent event) {
        return command.execute(createCommandContext(event))
            // TODO: properly handle the exception (log / send error message / ..)
            .onErrorContinue(CommandExecutionException.class, (exception, object) -> {});
    }

    private CommandContext createCommandContext(@NotNull MessageCreateEvent event) {
        List<String> arguments = Arrays.stream(event.getMessage()
                .getContent()
                .replace(this.prefix, "")
                .split("\s+"))
                .skip(1)
                .collect(Collectors.toList());

        // Member is already verified in {@link shouldHandle}
        //noinspection OptionalGetWithoutIsPresent
        return new CommandContext(
            event,
            event.getMember().get(),
            event.getGuild(),
            event.getMessage().getChannel(),
            event.getMessage().getUserMentionIds(),
            event.getMessage().getRoleMentionIds(),
            arguments
        );
    }

    private boolean shouldHandle(@NotNull MessageCreateEvent event) {
        String content = event.getMessage().getContent();

        return event.getMember()
                .map(user -> !user.isBot() && content.startsWith(this.prefix))
                .orElse(false);
    }
}
