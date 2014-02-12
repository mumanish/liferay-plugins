/**
 * Copyright (c) 2000-2013 Liferay, Inc. All rights reserved.
 *
 * This library is free software; you can redistribute it and/or modify it under
 * the terms of the GNU Lesser General Public License as published by the Free
 * Software Foundation; either version 2.1 of the License, or (at your option)
 * any later version.
 *
 * This library is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for more
 * details.
 */

package com.liferay.sync.engine;

import com.liferay.sync.engine.documentlibrary.event.GetAllSyncDLObjectsEvent;
import com.liferay.sync.engine.filesystem.SyncSiteWatchEventListener;
import com.liferay.sync.engine.filesystem.SyncWatchEventProcessor;
import com.liferay.sync.engine.filesystem.WatchEventListener;
import com.liferay.sync.engine.filesystem.Watcher;
import com.liferay.sync.engine.model.SyncAccount;
import com.liferay.sync.engine.model.SyncSite;
import com.liferay.sync.engine.service.SyncAccountService;
import com.liferay.sync.engine.service.SyncSiteService;
import com.liferay.sync.engine.upgrade.util.UpgradeUtil;
import com.liferay.sync.engine.util.LoggerUtil;
import com.liferay.sync.engine.util.PropsValues;
import com.liferay.sync.engine.util.SyncEngineUtil;

import java.nio.file.Path;
import java.nio.file.Paths;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Shinn Lok
 */
public class SyncEngine {

	public static void start() {
		try {
			doStart();
		}
		catch (Exception e) {
			_logger.error(e.getMessage(), e);
		}
	}

	protected static void doStart() throws Exception {
		SyncEngineUtil.fireSyncEngineStateChanged(
			SyncEngineUtil.SYNC_ENGINE_STATE_STARTING);

		LoggerUtil.initLogger();

		_logger.info("Starting " + PropsValues.SYNC_PRODUCT_NAME);

		UpgradeUtil.upgrade();

		SyncWatchEventProcessor syncWatchEventProcessor =
				new SyncWatchEventProcessor();

		syncWatchEventProcessor.process();

		for (SyncAccount syncAccount : SyncAccountService.findAll()) {
			List<SyncSite> syncSites = SyncSiteService.findSyncSites(
				syncAccount.getSyncAccountId());

			for (SyncSite syncSite : syncSites) {
				Map<String, Object> map = new HashMap<String, Object>();

				map.put("folderId", 0);
				map.put("repositoryId", syncSite.getGroupId());

				_scheduledExecutorService.scheduleAtFixedRate(
					new GetAllSyncDLObjectsEvent(
						syncAccount.getSyncAccountId(), map),
					0, syncAccount.getInterval(), TimeUnit.SECONDS);
			}

			Path filePath = Paths.get(syncAccount.getFilePathName());

			WatchEventListener watchEventListener =
				new SyncSiteWatchEventListener(syncAccount.getSyncAccountId());

			Watcher watcher = new Watcher(filePath, true, watchEventListener);

			_executorService.execute(watcher);
		}

		SyncEngineUtil.fireSyncEngineStateChanged(
			SyncEngineUtil.SYNC_ENGINE_STATE_STARTED);
	}

	private static Logger _logger = LoggerFactory.getLogger(SyncEngine.class);

	private static ExecutorService _executorService =
		Executors.newCachedThreadPool();
	private static ScheduledExecutorService _scheduledExecutorService =
		Executors.newScheduledThreadPool(5);

}