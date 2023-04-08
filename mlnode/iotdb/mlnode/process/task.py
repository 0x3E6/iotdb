# Licensed to the Apache Software Foundation (ASF) under one
# or more contributor license agreements.  See the NOTICE file
# distributed with this work for additional information
# regarding copyright ownership.  The ASF licenses this file
# to you under the Apache License, Version 2.0 (the
# "License"); you may not use this file except in compliance
# with the License.  You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing,
# software distributed under the License is distributed on an
# "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
# KIND, either express or implied.  See the License for the
# specific language governing permissions and limitations
# under the License.
#

import os
from abc import abstractmethod

import optuna

from iotdb.mlnode.log import logger
from iotdb.mlnode.process.trial import ForecastingTrainingTrial
from iotdb.mlnode.algorithm.factory import create_forecast_model
from iotdb.mlnode.client import client_manager
from iotdb.thrift.common.ttypes import TrainingState

class TrainingTrialObjective:
    """
    A class which serve as a function, should accept trial as args
    and return the optimization objective.
    Optuna will try to minimize the objective.
    """

    def __init__(self, trial_configs, model_configs, dataset, task_trial_map):
        self.trial_configs = trial_configs
        self.model_configs = model_configs
        self.dataset = dataset
        self.task_trial_map = task_trial_map

    def __call__(self, trial: optuna.Trial):
        # TODO: decide which parameters to tune
        trial_configs = self.trial_configs
        trial_configs['learning_rate'] = trial.suggest_float("lr", 1e-7, 1e-1, log=True)
        trial_configs['trial_id'] = 'tid_' + str(trial._trial_id)
        # TODO: check args
        model, model_cfg = create_forecast_model(**self.model_configs)
        self.task_trial_map[self.trial_configs['model_id']][trial._trial_id] = os.getpid()
        _trial = ForecastingTrainingTrial(trial_configs, model, self.model_configs, self.dataset)
        loss = _trial.start()
        print(trial._trial_id, loss)
        return loss


class _BasicTask(object):
    """
    This class serve as a function, accepting configs and launch trials
    according to the configs.
    """

    def __init__(self, task_configs, model_configs, model, dataset, task_trial_map):
        self.task_trial_map = task_trial_map
        self.task_configs = task_configs
        self.model_configs = model_configs
        self.model = model
        self.dataset = dataset

    @abstractmethod
    def __call__(self):
        raise NotImplementedError


class ForecastingTrainingTask(_BasicTask):
    def __init__(self, task_configs, model_configs, model, dataset, task_trial_map):
        super(ForecastingTrainingTask, self).__init__(task_configs, model_configs, model, dataset, task_trial_map)
        self.model_id = self.task_configs['model_id']
        self.tuning = self.task_configs['tuning']
        self.confignode_client = client_manager.borrow_config_node_client()

        if self.tuning:
            self.study = optuna.create_study(direction='minimize')
        else:
            self.default_trial_id = 'tid_0'
            self.task_configs['trial_id'] = self.default_trial_id
            self.trial = ForecastingTrainingTrial(self.task_configs, self.model, self.model_configs, self.dataset)
            self.task_trial_map[self.model_id][self.default_trial_id] = os.getpid()

    def __call__(self):
        try:
            if self.tuning:
                self.study.optimize(TrainingTrialObjective(
                    self.task_configs,
                    self.model_configs,
                    self.dataset,
                    self.task_trial_map
                ), n_trials=20)
                best_trial_id = 'tid_' + str(self.study.best_trial._trial_id)
                self.confignode_client.update_model_state(self.model_id, TrainingState.FINISHED, best_trial_id)
            else:
                self.trial.start()
                self.confignode_client.update_model_state(self.model_id, TrainingState.FINISHED, self.default_trial_id)
        except Exception as e:
            logger.warn(e)
            raise e
