package ru.pulsar.jenkins.library.steps

import ru.pulsar.jenkins.library.edt.EdtCliEngineFactory
import ru.pulsar.jenkins.library.IStepExecutor
import ru.pulsar.jenkins.library.configuration.JobConfiguration
import ru.pulsar.jenkins.library.configuration.SourceFormat
import ru.pulsar.jenkins.library.ioc.ContextRegistry
import ru.pulsar.jenkins.library.utils.EDT
import ru.pulsar.jenkins.library.utils.FileUtils
import ru.pulsar.jenkins.library.utils.Logger

class EdtValidate implements Serializable {

    public static final String RESULT_STASH = 'edt-validate'
    public static final String RESULT_FILE = 'build/out/edt-validate.out'

    private final JobConfiguration config

    EdtValidate(JobConfiguration config) {
        this.config = config
    }

    def run() {
        IStepExecutor steps = ContextRegistry.getContext().getStepExecutor()

        Logger.printLocation()

        if (!config.stageFlags.edtValidate) {
            Logger.println("EDT validate step is disabled")
            return
        }

        def env = steps.env()
        def workspaceDir = FileUtils.getFilePath("$env.WORKSPACE/$DesignerToEdtFormatTransformation.WORKSPACE")

        String projectList
        if (config.sourceFormat == SourceFormat.DESIGNER) {

            // рабочая область в формате EDT уже была сформирована ранее,
            // поэтому надо получить ее из stash

            steps.unstash(DesignerToEdtFormatTransformation.WORKSPACE_ZIP_STASH)
            steps.unzip(DesignerToEdtFormatTransformation.WORKSPACE, DesignerToEdtFormatTransformation.WORKSPACE_ZIP)

            projectList = FileUtils.getFilePath("$workspaceDir/$EDT.EDT_PROJECT_NAME")

        } else {
            def srcDir = config.srcDir
            projectList = FileUtils.getFilePath("$env.WORKSPACE/$srcDir")
        }

        Logger.println("Выполнение валидации EDT")

        def engine = EdtCliEngineFactory.getEngine(config.edtVersion)

        engine.edtValidate(steps, config, projectList)

        steps.archiveArtifacts("$DesignerToEdtFormatTransformation.WORKSPACE/.metadata/.log")
        steps.archiveArtifacts(RESULT_FILE)
        steps.stash(RESULT_STASH, RESULT_FILE)
    }
}
