package me.vinceh121.gmcserver.event;

import io.vertx.core.json.JsonObject;

public class Intent {
	private final String name;
	private final JsonObject extras;

	public Intent(final String name, final JsonObject extras) {
		this.name = name;
		this.extras = extras;
	}

	public Intent(final String name) {
		this(name, new JsonObject());
	}

	public String getName() {
		return this.name;
	}

	public JsonObject getExtras() {
		return this.extras;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + (this.extras == null ? 0 : this.extras.hashCode());
		result = prime * result + (this.name == null ? 0 : this.name.hashCode());
		return result;
	}

	@Override
	public boolean equals(final Object obj) {
		if (this == obj) {
			return true;
		}
		if (!(obj instanceof Intent)) {
			return false;
		}
		final Intent other = (Intent) obj;
		if (this.extras == null) {
			if (other.extras != null) {
				return false;
			}
		} else if (!this.extras.equals(other.extras)) {
			return false;
		}
		if (this.name == null) {
			if (other.name != null) {
				return false;
			}
		} else if (!this.name.equals(other.name)) {
			return false;
		}
		return true;
	}

	public JsonObject toJson() {
		return JsonObject.mapFrom(this);
	}

	@Override
	public String toString() {
		return "Intent [name=" + this.name + ", extras=" + this.extras + "]";
	}

}
