package ru.pulsar.jenkins.library.utils

import ru.pulsar.jenkins.library.configuration.JobConfiguration

final class EDT {

    public static final String EDT_PROJECT_NAME = 'cf'

    static String ringModule(JobConfiguration config) {
        return config.edtAgentLabel()
    }

}

