package ru.pulsar.jenkins.library.steps

import org.jenkinsci.plugins.pipeline.utility.steps.fs.FileWrapper
import ru.pulsar.jenkins.library.IStepExecutor
import ru.pulsar.jenkins.library.configuration.JobConfiguration
import ru.pulsar.jenkins.library.ioc.ContextRegistry
import ru.pulsar.jenkins.library.utils.Logger
import ru.pulsar.jenkins.library.utils.VRunner

class InitInfoBase implements Serializable {

    private final JobConfiguration config

    InitInfoBase(JobConfiguration config) {
        this.config = config
    }

    def run() {
        IStepExecutor steps = ContextRegistry.getContext().getStepExecutor()

        Logger.printLocation()

        steps.createDir('build/out')

        if (!config.stageFlags.initSteps) {
            Logger.println("Init step is disabled")
            return
        }

        List<String> logosConfig = ["LOGOS_CONFIG=$config.logosConfig"]
        steps.withEnv(logosConfig) {

            String vrunnerPath = VRunner.getVRunnerPath()

            // Нужны ли настройки vrunner
            def options = config.initInfoBaseOptions
            String settingsIncrement = ''
            String vrunnerSettings = options.vrunnerSettings
            if (config.templateDBLoaded() && steps.fileExists(vrunnerSettings)) {
                settingsIncrement = " --settings $vrunnerSettings"
            }

            if (options.runMigration) {
                Logger.println("Запуск миграции ИБ")

                String command = vrunnerPath + ' run --command "ЗапуститьОбновлениеИнформационнойБазы;ЗавершитьРаботуСистемы;" --execute '
                String executeParameter = '$runnerRoot/epf/ЗакрытьПредприятие.epf'
                if (steps.isUnix()) {
                    executeParameter = '\\' + executeParameter
                }
                command += executeParameter
                command += ' --ibconnection "/F./build/ib"'

                command += settingsIncrement
                // Запуск миграции
                steps.catchError {
                    VRunner.exec(command)
                }
            } else {
                Logger.println("Шаг миграции ИБ выключен")
            }

            steps.catchError {

                List<Integer> returnStatuses = []

                if (options.additionalInitializationSteps.length == 0) {
                    FileWrapper[] files = steps.findFiles("tools/vrunner.init*.json")
                    files = files.sort new OrderBy( { it.name })
                    files.each {
                        Logger.println("Первичная инициализация файлом ${it.path}")
                        Integer returnStatus = VRunner.exec("$vrunnerPath vanessa --settings ${it.path} --ibconnection \"/F./build/ib\"", true)
                        returnStatuses.add(returnStatus)
                    }
                } else {
                    options.additionalInitializationSteps.each {
                        Logger.println("Первичная инициализация командой ${it}")
                        Integer returnStatus = VRunner.exec("$vrunnerPath ${it} --ibconnection \"/F./build/ib\"${settingsIncrement}", true)
                        returnStatuses.add(returnStatus)
                    }
                }

                if (Collections.max(returnStatuses) >= 2) {
                    steps.error("Получен неожиданный/неверный результат работы шагов инициализации ИБ. Возможно, имеется ошибка в параметрах запуска vanessa-runner")
                } else if (returnStatuses.contains(1)) {
                    steps.unstable("Инициализация ИБ завершилась, но некоторые ее шаги не выполнились корректно")
                } else {
                    Logger.println("Инициализация ИБ завершилась успешно")
                }
            }
        }

        steps.stash('init-allure', 'build/out/allure/**', true)
        steps.stash('init-cucumber', 'build/out/cucumber/**', true)
    }
}
