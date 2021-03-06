package me.vinceh121.gmcserver.managers.email;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import io.vertx.core.json.pointer.JsonPointer;
import io.vertx.ext.mail.MailClient;
import io.vertx.ext.mail.MailConfig;
import io.vertx.ext.mail.MailMessage;
import me.vinceh121.gmcserver.GMCBuild;
import me.vinceh121.gmcserver.GMCServer;
import me.vinceh121.gmcserver.managers.AbstractManager;

public class EmailManager extends AbstractManager {
	public static final Pattern VAR_PATTERN = Pattern.compile("\\{\\{[a-zA-Z/-_]+\\}\\}");
	private final MailClient client;
	private final String from;

	public EmailManager(final GMCServer srv) {
		super(srv);
		try {
			this.client = MailClient.create(srv.getVertx(), new MailConfig(
					new JsonObject(new String(Files.readAllBytes(Paths.get(GMCBuild.VERTX_MAIL_CONFIG_PATH))))));
		} catch (final IOException e) {
			this.log.error("Failed to read mail config", e);
			throw new RuntimeException(e);
		}
		this.from = this.srv.getConfig().getProperty("email.from"); // TODO default value from instance host
	}

	public void sendEmail(final Email email) {
		this.fillStandardContext(email.getContext());

		this.buildEmail(email.getTemplate() + ".html", email.getContext()).onComplete(buildRes -> {
			if (buildRes.failed()) {
				this.log.error("Error while building email", buildRes.cause());
				return;
			}

			final String content = buildRes.result();

			final MailMessage message = new MailMessage();
			message.setFrom(this.from);
			message.setTo(email.getTo().getUsername() + " <" + email.getTo().getEmail() + ">");
			message.setHtml(content);
			message.setSubject(email.getSubject());

			this.client.sendMail(message, mailRes -> {
				if (mailRes.failed()) {
					this.log.error("Failed to send email", mailRes.cause());
				} else {
					this.log.info("Email sent to {}", email.getTo());
				}
			});
		});
	}

	private void fillStandardContext(final JsonObject obj) {
		obj.put("instance", JsonObject.mapFrom(this.srv.getInstanceInfo()));

	}

	public Future<String> buildEmail(final String template, final JsonObject context) {
		return Future.future(p -> {
			final String raw = this.readRaw(template);
			final String resolved = this.resolveVariables(raw, context);
			p.complete(resolved);
		});
	}

	private String readRaw(final String template) {
		return this.srv.getVertx().fileSystem().readFileBlocking(GMCBuild.MAIL_TEMPLATES_PATH + template).toString();
	}

	private String resolveVariables(final String rawHtml, final JsonObject context) throws IllegalArgumentException {
		final StringBuilder sb = new StringBuilder(rawHtml);
		Matcher matcher;
		while ((matcher = EmailManager.VAR_PATTERN.matcher(sb)).find()) {
			final String path = matcher.group().substring(2, matcher.end() - matcher.start() - 2); // strip {{ & }}
			final JsonPointer pointer = JsonPointer.from("/" + path);
			final String value = String.valueOf(pointer.queryJson(context));

			sb.replace(matcher.start(), matcher.end(), value);
		}
		return sb.toString();
	}

}
