/*
 * Copyright (c) 2000-2018 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

"use strict";

let uuid = require("uuid");

let timestamp = require("../../proto/main/js/google/protobuf/timestamp_pb.js");

let query = require("../../proto/main/js/spine/client/query_pb.js");
let entities = require("../../proto/main/js/spine/client/entities_pb.js");
let actorContext = require("../../proto/main/js/spine/core/actor_context_pb");
let command = require("../../proto/main/js/spine/core/command_pb.js");
let userId = require("../../proto/main/js/spine/core/user_id_pb");
let timeZone = require("../../proto/main/js/spine/time/zone_pb");

let {TypeUrl, TypedMessage} = require("./typed-message");

/**
 * The type URL representing the spine.core.Command.
 *
 * @type {TypeUrl}
 */
const COMMAND_MESSAGE_TYPE = new TypeUrl("type.spine.io/spine.core.Command");

/**
 * The type URL representing the spine.client.Query.
 *
 * @type {TypeUrl}
 */
const QUERY_MESSAGE_TYPE = new TypeUrl("type.spine.io/spine.client.Query");

/**
 * A factory for the various requests fired from the client-side by an actor.
 */
export class ActorRequestFactory {

  /**
   * Creates a new instance of ActorRequestFactory for the given actor.
   *
   * @param actor a string identifier of an actor
   */
  constructor(actor) {
    this._actor = new userId.UserId();
    this._actor.setValue(actor);
  }

  /**
   * Creates a Query targeting all the instances of the given type.
   *
   * @param typeUrl the type URL of the target type; represented as a string
   * @return a {@link TypedMessage} of the built query
   */
  queryAll(typeUrl) {
    let target = new entities.Target();
    target.setType(typeUrl.value);
    target.setIncludeAll(true);
    let result = this._query(target);
    return result;
  }

  /**
   * Creates a Query targeting a specific instance of the given type.
   *
   * @param typeUrl the type URL of the target type; represented as a string
   * @param id      the ID of the instance targeted by this query, represented
   *                as a {@link TypedMessage}
   * @return a {@link TypedMessage} of the built query
   */
  queryById(typeUrl, id) {
    let target = new entities.Target();
    target.setType(typeUrl);

    let entityId = new entities.EntityId();
    entityId.setId(id.toAny());
    let idFilter = new entities.EntityIdFilter();
    idFilter.addIds(entityId);
    let filters = new entities.EntityFilters();
    filters.setIdFilter(idFilter);
    target.setFilters(filters);

    let result = this._query(target);
    return result;
  }

  /**
   * Creates a Command from the given command message.
   *
   * @param message the command message, represented as a {@link TypedMessage}
   * @returns a {@link TypedMessage} of the built command
   */
  command(message) {
    let id = ActorRequestFactory._newCommandId();
    let messageAny = message.toAny();
    let context = this._commandContext();

    let result = new command.Command();
    result.setId(id);
    result.setMessage(messageAny);
    result.setContext(context);

    let typedResult = new TypedMessage(result, COMMAND_MESSAGE_TYPE);
    return typedResult;
  }

  _query(target) {
    let id = ActorRequestFactory._newQueryId();
    let actorContext = this._actorContext();

    let result = new query.Query();
    result.setId(id);
    result.setTarget(target);
    result.setContext(actorContext);

    let typedResult = new TypedMessage(result, QUERY_MESSAGE_TYPE);
    return typedResult;
  }

  _actorContext() {
    let result = new actorContext.ActorContext();
    result.setActor(this._actor);
    let seconds = new Date().getUTCSeconds();
    let time = new timestamp.Timestamp();
    time.setSeconds(seconds);
    result.setTimestamp(time);
    result.setZoneOffset(ActorRequestFactory._zoneOffset());
    return result;
  }

  _commandContext() {
    let result = new command.CommandContext();
    let actorContext = this._actorContext();
    result.setActorContext(actorContext);
    return result;
  }

  static _zoneOffset() {
    let timeOptions = Intl.DateTimeFormat().resolvedOptions();
    let zoneId = new timeZone.ZoneId();
    zoneId.setValue(timeOptions.timeZone);

    let zoneOffset = ActorRequestFactory._zoneOffsetSeconds();

    let result = new timeZone.ZoneOffset();
    result.setAmountSeconds(zoneOffset);
    result.setId(zoneId);
    return result;
  }

  static _zoneOffsetSeconds() {
    return new Date().getTimezoneOffset() * 60;
  }

  static _newQueryId() {
    let result = new query.QueryId();
    result.setValue("q-" + uuid.v4());
    return result;
  }

  static _newCommandId() {
    let result = new command.CommandId();
    result.setUuid(uuid.v4());
    return result;
  }
}
