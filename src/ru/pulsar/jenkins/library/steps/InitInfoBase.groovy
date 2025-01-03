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

            Map<String, Integer> exitStatuses = new LinkedHashMap<>()

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
                def migrationStatusFile = "build/out/migration-exit-status.log"
                command += " --exitCodePath \"${migrationStatusFile}\""
                // Запуск миграции
                steps.catchError {
                    VRunner.exec(command)
                    exitStatuses.put(command, readExitStatusFromFile(migrationStatusFile))
                }
            } else {
                Logger.println("Шаг миграции ИБ выключен")
            }

            steps.catchError {

                if (options.additionalInitializationSteps.length == 0) {
                    FileWrapper[] files = steps.findFiles("tools/vrunner.init*.json")
                    files = files.sort new OrderBy({ it.name })
                    files.each {
                        Logger.println("Первичная инициализация файлом ${it.path}")
                        def command = "$vrunnerPath vanessa --settings ${it.path} --ibconnection \"/F./build/ib\""
                        Integer exitStatus = VRunner.exec(command, true)
                        exitStatuses.put(command, exitStatus)
                    }
                } else {
                    options.additionalInitializationSteps.each {
                        Logger.println("Первичная инициализация командой ${it}")
                        def command = "$vrunnerPath ${it} --ibconnection \"/F./build/ib\"${settingsIncrement}"
                        Integer exitStatus = VRunner.exec(command, true)
                        exitStatuses.put(command, exitStatus)
                    }
                }

                if (Collections.max(exitStatuses.values()) >= 2) {
                    steps.error("Получен неожиданный/неверный результат работы шагов инициализации ИБ. Возможно, имеется ошибка в параметрах запуска vanessa-runner")
                } else if (exitStatuses.values().contains(1)) {
                    steps.unstable("Инициализация ИБ завершилась, но некоторые ее шаги выполнились некорректно")
                } else {
                    Logger.println("Инициализация ИБ завершилась успешно")
                }

                exitStatuses.each { key, value ->
                    Logger.println("${key}: status ${value}")
                }

            }
        }

        steps.stash('init-allure', 'build/out/allure/**', true)
        steps.stash('init-cucumber', 'build/out/cucumber/**', true)
    }

    static Integer readExitStatusFromFile(String path) {

        IStepExecutor steps = ContextRegistry.getContext().getStepExecutor()

        try {

            String content = steps.readFile(path)
            if (content.isEmpty()) {
                return 1
            }

            int exitStatus = content.toInteger()
            return exitStatus

        } catch (Exception ignored) {
            return 1
        }
    }
}
