package me.vinceh121.gmcserver.modules;

import java.util.Date;
import java.util.List;

import org.bson.types.ObjectId;

import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import me.vinceh121.gmcserver.GMCServer;
import me.vinceh121.gmcserver.entities.Device;
import me.vinceh121.gmcserver.entities.Record;
import me.vinceh121.gmcserver.entities.User;
import me.vinceh121.gmcserver.handlers.AuthHandler;
import me.vinceh121.gmcserver.managers.DeviceManager;
import me.vinceh121.gmcserver.managers.DeviceManager.CreateDeviceAction;
import me.vinceh121.gmcserver.managers.DeviceManager.DeleteDeviceAction;
import me.vinceh121.gmcserver.managers.DeviceManager.DeviceStatsAction;
import me.vinceh121.gmcserver.managers.DeviceManager.DeviceTimelineAction;
import me.vinceh121.gmcserver.managers.DeviceManager.GetDeviceAction;
import me.vinceh121.gmcserver.managers.DeviceManager.UpdateDeviceAction;
import me.vinceh121.gmcserver.managers.UserManager;
import me.vinceh121.gmcserver.managers.UserManager.GetUserAction;

public class DeviceModule extends AbstractModule {

	public DeviceModule(final GMCServer srv) {
		super(srv);
		this.registerStrictAuthedRoute(HttpMethod.POST, "/device", this::handleCreateDevice);
		this.registerStrictAuthedRoute(HttpMethod.DELETE, "/device/:deviceId", this::handleRemoveDevice);
		this.registerStrictAuthedRoute(HttpMethod.PUT, "/device/:deviceId", this::handleUpdateDevice);
		this.registerAuthedRoute(HttpMethod.GET, "/device/:deviceId", this::handleDevice);
		this.registerAuthedRoute(HttpMethod.GET, "/device/:deviceId/timeline", this::handleDeviceHistory);
		this.registerRoute(HttpMethod.GET, "/device/:deviceId/stats/:field", this::handleStats);
	}

	private void handleCreateDevice(final RoutingContext ctx) {
		final JsonObject obj = ctx.getBodyAsJson();

		final User user = ctx.get(AuthHandler.USER_KEY);

		final String name = obj.getString("name");
		if (name == null) {
			this.error(ctx, 400, "Missing parameter name");
			return;
		}

		final JsonArray arrLoc = obj.getJsonArray("position");
		if (arrLoc == null) {
			this.error(ctx, 400, "Missing parameter position");
			return;
		}

		final CreateDeviceAction action = this.srv.getManager(DeviceManager.class)
				.createDevice()
				.setUser(user)
				.setArrLocation(arrLoc)
				.setName(name);
		action.execute().onComplete(res -> {
			if (res.failed()) {
				this.error(ctx, 400, res.cause().getMessage());
				return;
			}
			ctx.response().end(res.result().toJson().toBuffer());
		});
	}

	private void handleRemoveDevice(final RoutingContext ctx) {
		final String rawDeviceId = ctx.pathParam("deviceId");

		final ObjectId deviceId;
		try {
			deviceId = new ObjectId(rawDeviceId);
		} catch (final IllegalArgumentException e) {
			this.error(ctx, 400, "Invalid device ID");
			return;
		}

		final User user = ctx.get(AuthHandler.USER_KEY);

		final JsonObject obj = ctx.getBodyAsJson();
		final boolean delete = obj.getBoolean("delete");

		final DeleteDeviceAction action = this.srv.getManager(DeviceManager.class)
				.deleteDevice()
				.setDelete(delete)
				.setDeviceId(deviceId)
				.setUser(user);

		action.execute().onComplete(res -> {
			if (res.failed()) {
				this.error(ctx, 400, res.cause().getMessage());
				return;
			}

			ctx.response().end(new JsonObject().put("delete", delete).toBuffer());
		});
	}

	private void handleUpdateDevice(final RoutingContext ctx) {
		final String rawDeviceId = ctx.pathParam("deviceId"); // TODO make action somehow

		final ObjectId deviceId;
		try {
			deviceId = new ObjectId(rawDeviceId);
		} catch (final IllegalArgumentException e) {
			this.error(ctx, 400, "Invalid device ID");
			return;
		}

		final JsonObject obj = ctx.getBodyAsJson();

		final GetDeviceAction getAction = this.srv.getManager(DeviceManager.class).getDevice().setId(deviceId);
		getAction.execute().onComplete(res -> {
			if (res.failed()) {
				this.error(ctx, 404, res.cause().getMessage());
				return;
			}

			final Device dev = res.result();
			final User user = ctx.get(AuthHandler.USER_KEY);

			if (!user.getId().equals(dev.getOwner())) {
				this.error(ctx, 403, "Not owner of the device");
				return;
			}

			final UpdateDeviceAction action = this.srv.getManager(DeviceManager.class)
					.updateDevice()
					.setDevice(dev)
					.setArrLocation(obj.getJsonArray("location"))
					.setModel(obj.getString("model"))
					.setName(obj.getString("name"));
			action.execute().onComplete(upRes -> {
				if (upRes.failed()) {
					this.error(ctx, 500, upRes.cause().getMessage());
					return;
				}

				ctx.response().end(new JsonObject().put("changed", upRes.result()).toBuffer());
			});
		});

	}

	private void handleDevice(final RoutingContext ctx) {
		final String rawDeviceId = ctx.pathParam("deviceId");

		final ObjectId deviceId;
		try {
			deviceId = new ObjectId(rawDeviceId);
		} catch (final IllegalArgumentException e) {
			this.error(ctx, 400, "Invalid device ID");
			return;
		}

		final GetDeviceAction action = this.srv.getManager(DeviceManager.class).getDevice().setId(deviceId);

		action.execute().onComplete(res -> {
			if (res.failed()) {
				this.error(ctx, 404, res.cause().getMessage());
				return;
			}

			final Device dev = res.result();

			final User user = ctx.get(AuthHandler.USER_KEY);

			final GetUserAction getOwnerAction = this.srv.getManager(UserManager.class).getUser().setId(dev.getOwner());
			getOwnerAction.execute().onComplete(ures -> {
				final boolean own = user != null && user.getId().equals(dev.getOwner());

				final JsonObject obj = own ? dev.toJson() : dev.toPublicJson();
				obj.put("own", own);
				obj.put("owner", ures.result().toPublicJson());

				ctx.response().end(obj.toBuffer());
			});
		});
	}

	private void handleDeviceHistory(final RoutingContext ctx) {
		final User user = ctx.get(AuthHandler.USER_KEY);
		final String rawDeviceId = ctx.pathParam("deviceId");

		final ObjectId deviceId;
		try {
			deviceId = new ObjectId(rawDeviceId);
		} catch (final IllegalArgumentException e) {
			this.error(ctx, 400, "Invalid device ID");
			return;
		}

		final GetDeviceAction getAction = this.srv.getManager(DeviceManager.class).getDevice().setId(deviceId);
		getAction.execute().onComplete(getRes -> {
			if (getRes.failed()) {
				this.error(ctx, 404, "Device not found");
				return;
			}

			final Device dev = getRes.result();

			final Date start, end;

			if (ctx.request().params().contains("start")) {
				try {
					start = new Date(Long.parseLong(ctx.request().getParam("start")));
				} catch (final NumberFormatException e) {
					this.error(ctx, 400, "Format error in start date");
					return;
				}
			} else {
				start = null;
			}

			if (ctx.request().params().contains("end")) {
				try {
					end = new Date(Long.parseLong(ctx.request().getParam("end")));
				} catch (final NumberFormatException e) {
					this.error(ctx, 400, "Format error in end date");
					return;
				}
			} else {
				end = null;
			}

			final boolean full = "y".equals(ctx.request().getParam("full"));

			final DeviceTimelineAction histAction = this.srv.getManager(DeviceManager.class)
					.deviceTimeline()
					.setStart(start)
					.setEnd(end)
					.setFull(full)
					.setRequester(user)
					.setDev(dev);
			histAction.execute().onComplete(histRes -> {
				if (histRes.failed()) {
					this.error(ctx, 500, histRes.cause().getMessage());
					return;
				}

				final JsonObject obj = new JsonObject();
				final JsonArray arr = new JsonArray();
				obj.put("records", arr);

				final List<Record> recs = histRes.result();

				if (user != null && user.getId().equals(dev.getOwner())) {
					recs.forEach(r -> arr.add(r.toJson()));
				} else {
					recs.forEach(r -> arr.add(r.toPublicJson()));
				}

				ctx.response().end(obj.toBuffer());
			});
		});

	}

	private void handleStats(final RoutingContext ctx) {
		final String rawDevId = ctx.pathParam("deviceId");

		final ObjectId devId;
		try {
			devId = new ObjectId(rawDevId);
		} catch (final IllegalArgumentException e) {
			this.error(ctx, 400, "Invalid ID");
			return;
		}

		final String field = ctx.pathParam("field");
		if (!Record.STAT_FIELDS.contains(field)) {
			this.error(ctx, 400, "Invalid field");
			return;
		}

		final GetDeviceAction getDevAction = this.srv.getManager(DeviceManager.class).getDevice().setId(devId);
		getDevAction.execute().onComplete(getRes -> {
			if (getRes.failed()) {
				this.error(ctx, 404, "Device not found");
				return;
			}

			final DeviceStatsAction action = this.srv.getManager(DeviceManager.class)
					.deviceStats()
					.setDevId(getRes.result().getId())
					.setField(field);

			action.execute().onComplete(res -> {
				final JsonObject obj = res.result().toJson();
				obj.remove("_id");

				ctx.response().end(obj.toBuffer());
			});
		});
	}

}
